/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.realtime.soundtransit.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.onebusaway.transit_data_federation.services.ExtendedCalendarService;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.ServiceIdActivation;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtimeConstants;

@Component
/**
 * Maps the GTFS-realtime protocol buffer models to the archiver models.
 * 
 */
public class FeedServiceImpl implements FeedService {
  private static Logger _log = LoggerFactory.getLogger(FeedServiceImpl.class);
  private static String PINE_STREET_STUB = "PNSS_PLAT"; // Not really a stop.
  private TransitGraphDao _transitGraphDao;
  private ExtendedCalendarService _calendarService;
  private Map<String, String> stopMapping = null;
  private static String _linkAgencyId;
  private static String _linkRouteId;
  private static String _linkStopMappingFile;
  private FeedMessage _currentVehiclePositions;
  private FeedMessage _currentTripUpdates;
  private List<TripEntry> tripEntries;
  private List<String> _stopsNotInGtfs = new ArrayList<String>();
  //private List<StopOffset> stopOffsets;
  private List<StopOffset> nbStopOffsets = new ArrayList<StopOffset>();// Northbound
  private List<StopOffset> sbStopOffsets = new ArrayList<StopOffset>();// Southbound
  private long timeToUpdateTripIds = 0;

  public void setLinkAgencyId(String linkAgencyId) {
    _linkAgencyId = linkAgencyId;
  }

  public void setLinkRouteId(String linkRouteId) {
  	_linkRouteId = linkRouteId;
  }
  
  public void setLinkStopMappingFile(String linkStopMappingFile) {
    _linkStopMappingFile = linkStopMappingFile;
  }

  @Autowired
  public void setTransitGraphDao(TransitGraphDao transitGraphDao) {
    _transitGraphDao = transitGraphDao;
  }

  @Autowired
  public void set_calendarService(ExtendedCalendarService _calendarService) {
    this._calendarService = _calendarService;
  }

  public FeedMessage getCurrentVehiclePositions() {
    return _currentVehiclePositions;
  }

  public void setCurrentVehiclePositions(FeedMessage currentVehiclePositions) {
    this._currentVehiclePositions = currentVehiclePositions;
  }

  public FeedMessage getCurrentTripUpdates() {
    return _currentTripUpdates;
  }

  public void setCurrentTripUpdates(FeedMessage currentTripUpdates) {
    this._currentTripUpdates = currentTripUpdates;
  }

  public void setTripEntries(List<TripEntry> tripEntries) {
    this.tripEntries = tripEntries;
  }

  public void setStopsNotInGtfs(List<String> stopsNotInGtfs) {
    _stopsNotInGtfs.addAll(stopsNotInGtfs);
  }

  public Map<String, String> getStopMapping() {
    return stopMapping;
  }

  public void setStopMapping(Map<String, String> stopMapping) {
    this.stopMapping = stopMapping;
  }

  public void init() {
    // Read in the AVL-GTFS stop mapping file
    try (BufferedReader br = new BufferedReader(new FileReader(_linkStopMappingFile))) {
      stopMapping = new HashMap<String, String>();
      String ln = "";
      while ((ln = br.readLine()) != null) {
        _log.info(ln);
        int idx = ln.indexOf(',');
        if (idx > 0) {
          stopMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
        }
      }
    } catch (IOException e) {
      _log.error("Error reading StopMapping file " + e.getMessage());
    }
    updateTripsAndStops();
    for (String stopId : _stopsNotInGtfs) {
      _log.info ("Not a stop: " + stopId);
    }
  }
  
  @Override
  public LinkAVLData parseAVLFeed(String feedData) {
    LinkAVLData linkAVLData = new LinkAVLData();
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(SerializationConfig.Feature.AUTO_DETECT_FIELDS, true);
    boolean parseFailed = false;
    try {
      linkAVLData = mapper.readValue(feedData, LinkAVLData.class);
      if (linkAVLData != null) {
        _log.debug("Parsed AVL data: " + mapper.writeValueAsString(linkAVLData));
      }
    } catch (JsonParseException e) {
      _log.error("JsonParseException trying to parse feed data.");
      parseFailed = true;
    } catch (JsonMappingException e) {
      _log.error("JsonMappingException: " + e.getMessage());
      parseFailed = true;
    } catch (IOException e) {
      _log.error("IOException trying to parse feed data.");
      parseFailed = true;
    } catch (Exception e) {
      _log.error("Exception trying to parse feed data: " + e.getMessage());
      parseFailed = true;
    }
    if (parseFailed) {
      return null;
    }
    // The AVL feed occasionally has dates from 1899.  That is because MySQL
    // will report a null date as 12-30-1899.
    // Convert any "1899" dates in ArrivalTime to null
    TripInfoList tripInfoList = linkAVLData.getTrips();
    if (tripInfoList != null) {
      List<TripInfo> trips = tripInfoList.getTrips();
      if (trips != null) {
        for (TripInfo trip : trips) {
          StopUpdatesList stopUpdatesList = trip.getStopUpdates();
          if (stopUpdatesList == null) {
            continue;
          }
          List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
          if (stopUpdates != null && stopUpdates.size() > 0) {
            for (StopUpdate stopTimeUpdate : stopUpdates) {
              ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
              if (arrivalTime != null) {
                String actual = arrivalTime.getActual();
                String estimated = arrivalTime.getEstimated();
                String scheduled = arrivalTime.getScheduled();
                if (actual != null && actual.startsWith("1899")) {
                  //actual = convert1899Date(actual);
                  arrivalTime.setActual(null);
                }
                if (estimated != null && estimated.startsWith("1899")) {
                  //estimated = convert1899Date(estimated);
                  arrivalTime.setEstimated(null);
                }
                if (scheduled != null && scheduled.startsWith("1899")) {
                  //scheduled = convert1899Date(scheduled);
                  arrivalTime.setScheduled(null);
                }
              }
            }
          }
        }
      }
    }
    // Sort StopUpdates in chronological order
    List<TripInfo> trips = null;
    StopUpdatesList stopUpdatesList = null;
    List<StopUpdate> stopUpdates = null;
    if (tripInfoList != null 
        && (trips = tripInfoList.getTrips()) != null) {
      for (TripInfo trip : trips) {
        if ((stopUpdatesList = trip.getStopUpdates()) != null 
            && (stopUpdates  = stopUpdatesList.getUpdates()) != null 
            && stopUpdates.size() > 1) {
          Collections.sort(stopUpdates, new StopUpdateComparator());
        }
      }
    }
    
    return linkAVLData;
  }
  
  /*
   * This method will compare two StopUpdates based on their ArrivalTime 
   * information.  It first checks ActualTime, if any, then EstimatedTime,
   * and finally ScheduledTime.
   */
  public class StopUpdateComparator implements Comparator<StopUpdate> {
    @Override
    public int compare(StopUpdate su1, StopUpdate su2) {
      // Check that both updates have ArrivalTime objects
      ArrivalTime arrivalTime1 = su1.getArrivalTime();
      ArrivalTime arrivalTime2 = su2.getArrivalTime();
      long arrival1 = (arrivalTime1 != null) ? 1 : 0;
      long arrival2 = (arrivalTime2 != null) ? 1 : 0;
      if (arrival1 == 0 || arrival2 == 0) {
        return (int)(arrival1 - arrival2);
      }
      
      arrival1 = parseArrivalTime(arrivalTime1.getActual());
      arrival2 = parseArrivalTime(arrivalTime2.getActual());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {  // one is zero, the other isn't
        return (arrival1 > arrival2) ? 0 : 1;  // Non-zero has arrived already
      }
        
      arrival1 = parseArrivalTime(arrivalTime1.getEstimated());
      arrival2 = parseArrivalTime(arrivalTime2.getEstimated());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {
        return (arrival1 > arrival2) ? 0 : 1;
      }
      arrival1 = parseArrivalTime(arrivalTime1.getScheduled());
      arrival2 = parseArrivalTime(arrivalTime2.getScheduled());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {
        return (arrival1 > arrival2) ? 0 : 1;
      }
      
      return 0;
    }
  }
  
  /*
   * This method will parse an ArrivalTime string and return 0 if it is null,
   * empty, or cannot be parsed, and will otherwise return the parsed time in
   * milliseconds.
   */
  private long parseArrivalTime(String arrivalTime) {
    long result = 0L;
    if (arrivalTime != null  && !arrivalTime.isEmpty()) {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
      try {
        result = formatter.parse(arrivalTime).getTime();
      } catch (Exception e) {
        result = 0L;
      }
    }
    return result;
  }
  
  public FeedMessage buildVPMessage(LinkAVLData linkAVLData) {
    _log.debug("Starting buildVPMessage");
    // Update the list of trips (done only if the date has changed
    updateTripsAndStops();
    
    int vehiclePositionEntityCount = 0;
    FeedMessage vehiclePositionsFM = null;
    FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
    FeedHeader.Builder header = FeedHeader.newBuilder();
    header.setTimestamp(System.currentTimeMillis()/1000);
    header.setIncrementality(Incrementality.FULL_DATASET);
    header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
    feedMessageBuilder.setHeader(header);
    TripInfoList tripInfoList = linkAVLData.getTrips();
    List<TripInfo> trips = tripInfoList != null ? tripInfoList.getTrips() : null;
    if (trips != null) {
      _log.debug("trips.size(): " + trips.size());
      for (TripInfo trip : trips) {
        _log.debug("trip id: " + trip.getTripId());
       // If there are no stop time updates, don't generate a VehiclePosition
        // for this trip.
        StopUpdatesList stopUpdateList = trip.getStopUpdates();
        List<StopUpdate> updateList = stopUpdateList != null ? 
            stopUpdateList.getUpdates() : null;
        if (updateList == null || updateList.size() < 2) {
          continue;
        }
        
        VehiclePosition.Builder vp = VehiclePosition.newBuilder();
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        Position.Builder positionBuilder = Position.newBuilder();
        String vehicleId = "";
        // Use trip id from AVL, which is actually their "train label", 
        // as vehicle id to avoid issues with vehicle id changing if train 
        // backs up.
        if (trip.getTripId() != null) {
          vehicleId = trip.getTripId();
        }
        vd.setId(vehicleId);
        vp.setVehicle(vd);

        // Add latitude and longitude for vehicle position
        try {
          float lat = Float.parseFloat(trip.getLat());
          positionBuilder.setLatitude(lat);
          float lon = Float.parseFloat(trip.getLon());
          positionBuilder.setLongitude(lon);
          vp.setPosition(positionBuilder);
        } catch (NullPointerException | NumberFormatException ex) {
          _log.info("Vehicle latitude/longitude for vehicle " + vehicleId
              + " could not be parsed");
        }
        // Set timestamp to trip LastUpdatedDate
        long timestamp = 0L;
        String lastUpdatedDate = trip.getLastUpdatedDate();
        if (lastUpdatedDate != null) {
          try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
            Date parsedDate = df.parse(lastUpdatedDate);
            timestamp = parsedDate.getTime() / 1000;
          } catch (Exception e) {
            _log.debug("Exception parsing LastUpdatedDate time: " + lastUpdatedDate);
          }
        }
        timestamp = timestamp != 0L ? timestamp : System.currentTimeMillis()/1000;
        vp.setTimestamp(timestamp);

        StopUpdate nextStop = findNextStopOnTrip(trip.getStopUpdates());
        if (nextStop == null) {
          _log.info("Cannot determine next stop id for trip " + trip.getTripId());
          continue;
        }
        String stopId = nextStop.getStopId();
        if (stopId == null) {
          stopId = "";
        } else {
          String direction = getTripDirection(trip);
          stopId = getGTFSStop(stopId, direction);
          if (stopId == null) {
            _log.info("Could not map stop: " + (String) trip.getLastStopId());
            stopId = "";
          }
        }
        vp.setStopId(stopId);
        
        VehiclePosition.VehicleStopStatus status = null;
        if (nextStop.getArrivalTime() != null 
            && nextStop.getArrivalTime().getActual() == null) {
          status = VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO;
        } else {  // Must be at end of trip
          status = VehiclePosition.VehicleStopStatus.STOPPED_AT;
        }
        vp.setCurrentStatus(status);
        TripDescriptor td = buildTripDescriptor(trip);
        vp.setTrip(td);

        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(vehicleId);
        entity.setVehicle(vp);
        feedMessageBuilder.addEntity(entity);
        ++vehiclePositionEntityCount;
      }
      _log.debug("trip loop finished");
    }
    vehiclePositionsFM = feedMessageBuilder.build();
    if (vehiclePositionsFM != null) {
      _currentVehiclePositions = vehiclePositionsFM;
    } else {
      vehiclePositionsFM = FeedMessage.getDefaultInstance();
    }
    _log.info("vehicle position updates: " + vehiclePositionEntityCount);
    _log.debug("finishing buildVPMessage");
    return vehiclePositionsFM;
  }

  public FeedMessage buildTUMessage(LinkAVLData linkAVLData) {
    _log.debug("Starting buildTUMessage");
    // Update the list of trips (done only if the date has changed)
    updateTripsAndStops();
    
    int tripUpdateEntityCount = 0;
    FeedMessage tripUpdatesFM = null;
    TripInfoList tripInfoList = linkAVLData.getTrips();
    FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
    FeedHeader.Builder header = FeedHeader.newBuilder();
    header.setTimestamp(System.currentTimeMillis()/1000);
    header.setIncrementality(Incrementality.FULL_DATASET);
    header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
    feedMessageBuilder.setHeader(header);
    List<TripInfo> trips = tripInfoList != null ? tripInfoList.getTrips() : null;
    _log.debug("Processing " + trips.size() + " trips");
    if (trips != null) {
      _log.debug("Starting trip loop");
      for (TripInfo trip : trips) {
        _log.debug("Building TU for trip " + trip.getTripId());
        StopUpdatesList stopUpdateList = trip.getStopUpdates();
       List<StopUpdate> updateList = stopUpdateList != null ? 
            stopUpdateList.getUpdates() : null;
        if (updateList == null || updateList.size() < 2) {
          continue;
        }
        TripUpdate.Builder tu = TripUpdate.newBuilder();
        // Build the StopTimeUpdates
        _log.debug("Building stop updates");
        List<StopTimeUpdate> stopTimeUpdates = buildStopTimeUpdateList(trip);
        _log.debug("Stop update list size: " + stopTimeUpdates.size());
        tu.addAllStopTimeUpdate(stopTimeUpdates);

        // Build the VehicleDescriptor
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        String vehicleId = "";
        // Use trip id as vehicle id to avoid issues with vehicle id
        // changing if train backs up.
        if (trip.getTripId() != null) {
          vehicleId = trip.getTripId();
        }
        vd.setId(vehicleId);
        tu.setVehicle(vd);

        // Build the TripDescriptor
        TripDescriptor td = buildTripDescriptor(trip);
        tu.setTrip(td);

        // Set timestamp to trip LastUpdatedDate
        long timestamp = 0L;
        String lastUpdatedDate = trip.getLastUpdatedDate();
        if (lastUpdatedDate != null) {
          try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
            Date parsedDate = df.parse(lastUpdatedDate);
            timestamp = parsedDate.getTime() / 1000;
          } catch (Exception e) {
            _log.debug("Exception parsing LastUpdatedDate time: " + lastUpdatedDate);
          }
        }
        timestamp = timestamp != 0L ? timestamp : System.currentTimeMillis()/1000;
        tu.setTimestamp(timestamp);
        FeedEntity.Builder entity = FeedEntity.newBuilder();
        // Use VehicleId for entity Id since that is unique per trip
        entity.setId(vehicleId);
        entity.setTripUpdate(tu);
        feedMessageBuilder.addEntity(entity);
        ++tripUpdateEntityCount;
      }
      _log.debug("Ending trip loop");
    } else {
      _log.info("buildTUMessage(): no trip data found");
    }
    tripUpdatesFM = feedMessageBuilder.build();
    if (tripUpdatesFM != null) {
      _currentTripUpdates = tripUpdatesFM;
    } else {
      tripUpdatesFM = FeedMessage.getDefaultInstance();
    }
    _log.info("trip updates: " + tripUpdateEntityCount);
    _log.debug("Ending buildTUMessage");
    return tripUpdatesFM;
  }

  private StopUpdate findNextStopOnTrip(StopUpdatesList stopUpdatesList) {
    // Check the times for the StopUpdates to determine which stop the vehicle
    // will reach next. That will be the stop with the earliest estimated
    // arrival time, but an actual time of null. If the trip is already
    // completed, i.e. every stop update has an actual arrival time, then an
    // empty string will be returned.
    stopUpdatesList = (stopUpdatesList == null ? (StopUpdatesList)Collections.EMPTY_LIST : stopUpdatesList); //Check for null
    StopUpdate nextStop = null;
    StopUpdate lastStop = null;
    // Initially, set nextStopTime to an arbitrarily high value.
    Calendar cal = Calendar.getInstance();
    cal.set(2099, 12, 31);
    Date nextStopTime = cal.getTime();
    List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
    if (stopUpdates != null && stopUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopUpdates) {
        if (stopTimeUpdate.getStopId() == null
            || stopTimeUpdate.getStopId().isEmpty()
            || _stopsNotInGtfs.contains(stopTimeUpdate.getStopId())) {
          continue;
        }          
        ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
        if (arrivalTime == null) {
          continue;
        }
        lastStop = stopTimeUpdate;
        String arrival = arrivalTime.getActual();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        if (arrival == null) { // No "Actual", so this stop hasn't been reached
                               // yet.
          arrival = arrivalTime.getEstimated();
          Date parsedDate = null;
          try {
            parsedDate = df.parse(arrival);
          } catch (ParseException e) {
            _log.error("Exception parsing Estimated time: " + arrival);
            parsedDate = nextStopTime;
          }
          if (parsedDate.before(nextStopTime)) {
            nextStopTime = parsedDate;
            nextStop = stopTimeUpdate;
          }
        }
      }
    }
    // If all stops have actual arrival times, the trip must have finished, 
    // so use the last stop instead.
    if (nextStop == null) {
      nextStop = lastStop;
    }
    return nextStop;
  }

  private Date getTripStartTime(StopUpdatesList stopTimeUpdatesList) {
    Calendar cal = Calendar.getInstance();
    cal.set(2099, 12, 31);
    Date tripStartTime = null;
    List<StopUpdate> stopTimeUpdates = stopTimeUpdatesList.getUpdates();
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        if (stopTimeUpdate.getStopId() == null
            || stopTimeUpdate.getStopId().isEmpty()
            || stopTimeUpdate.getStopId().equals(PINE_STREET_STUB)) {
          continue;
        }          
        ArrivalTime arrivalTimeDetails = stopTimeUpdate.getArrivalTime();
        String scheduledArrival = null;
        if (arrivalTimeDetails != null) {
          scheduledArrival = arrivalTimeDetails.getScheduled();
        }
        // If this is the earliest time, use it for the trip start time
        if (scheduledArrival != null) { 
          try {
            Date parsedDate = null;
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            parsedDate = df.parse(scheduledArrival);
            if ((tripStartTime == null) || (parsedDate.before(tripStartTime))) {
              tripStartTime = parsedDate;
            }
          } catch (Exception e) {
            System.out.println("Exception parsing Estimated time: "
                + scheduledArrival);
          }
        }
      }
    }
    return tripStartTime;
  }
  
  private List<StopTimeUpdate> buildStopTimeUpdateList(TripInfo trip) {
    List<StopTimeUpdate> stopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    StopUpdatesList stopUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopUpdates = stopUpdateData.getUpdates();
    //boolean lastCompletedStopAdded = false;
    if (stopUpdates != null && stopUpdates.size() > 0) {
      // If stopUpdates has any updates for Pine Street Stub, drop them
      // since there is no GTFS stop this could be mapped to.
      List<StopUpdate> modStopUpdates = new ArrayList<>();
      for (int i=0; i<stopUpdates.size(); ++i) {
        String stopId = stopUpdates.get(i).getStopId();
        if (!stopId.equals(PINE_STREET_STUB)) {
          modStopUpdates.add(stopUpdates.get(i));
        }
      }
      stopUpdates = modStopUpdates;
      //_log.debug("About to build dummy stop updates");
      //List<StopTimeUpdate> dummyStopTimeUpdates =
      //    buildPseudoStopTimeUpdates(stopUpdates, getTripDirection(trip));
      //_log.debug("Finished building dummy stop updates");
      //stopTimeUpdateList.addAll(dummyStopTimeUpdates);
      for (StopUpdate stopUpdate : stopUpdates) {
        if (stopUpdate.getStopId() == null
            || stopUpdate.getStopId().isEmpty()) {
          continue;
        }    
        ArrivalTime arrivalTimeDetails = stopUpdate.getArrivalTime();
        if (arrivalTimeDetails != null) {
          String arrivalTime = arrivalTimeDetails.getActual();
          if (arrivalTime == null) {
            arrivalTime = arrivalTimeDetails.getEstimated();
          }
          if (arrivalTime != null && !arrivalTime.isEmpty()) {
            StopTimeUpdate stopTimeUpdate = 
                buildStopTimeUpdate(stopUpdate.getStopId(),
                    arrivalTime, getTripDirection(trip), "");
            stopTimeUpdateList.add(stopTimeUpdate);
          }
        }
      }
    }
    return stopTimeUpdateList;
  }

  private TripDescriptor buildTripDescriptor(TripInfo trip) {
    TripDescriptor.Builder td = TripDescriptor.newBuilder();
    String stopId = "";
    int scheduledTime = 0;
    StopUpdatesList stopTimeUpdateData = trip != null ? trip.getStopUpdates() : null;
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData != null 
        ? stopTimeUpdateData.getUpdates() : null;
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        if (stopTimeUpdate.getStopId() == null
            || stopTimeUpdate.getStopId().isEmpty()
            || stopTimeUpdate.getStopId().equals(PINE_STREET_STUB)) {
          continue;
        }          
        stopId = stopTimeUpdate.getStopId();
        if (stopTimeUpdate.getArrivalTime() == null) {
          _log.debug("No arrival time info for trip " + trip.getTripId());
          continue;
        }
        String formattedTime = stopTimeUpdate.getArrivalTime().getScheduled();
        if (formattedTime ==  null) {
          formattedTime = stopTimeUpdate.getArrivalTime().getEstimated();
        }
        if (formattedTime ==  null) {
          formattedTime = stopTimeUpdate.getArrivalTime().getActual();
        }
        if (formattedTime ==  null) {
          _log.debug("No arrival time info for trip " + trip.getTripId());
          continue;
        }
        formattedTime = formattedTime.substring(formattedTime.indexOf('T')+1, formattedTime.indexOf('T') + 9);
        String[] timeArray = formattedTime.split(":");
        int hours = Integer.parseInt(timeArray[0]);
        int minutes = Integer.parseInt(timeArray[1]);
        scheduledTime = (hours * 60 + minutes) * 60;
        break;
      }
    }
    // stopId should now be the last stop on the line
    _log.debug("Stop id before mapping: " + stopId);
    String mappedStopId = null;
    if (stopId != null && !stopId.isEmpty() && trip != null) {
      String direction = getTripDirection(trip);
      mappedStopId = getGTFSStop(stopId, direction);
    }
    if (mappedStopId == null || mappedStopId.isEmpty() && trip != null) {
      _log.debug("mappedStopId is null for trip " + trip.getTripId() + " and stop " + trip.getLastStopName() 
          + ", stopid is " + stopId);
    } else {
      _log.debug("GTFS stop id: " + mappedStopId);
    }
    String direction = getTripDirection(trip);
    _log.debug("Get GTFS trip id for AVL trip " + trip.getTripId());
    String tripId = getTripForStop(mappedStopId, direction, scheduledTime);
    if (tripId == null) {
      _log.debug("trip id is null");
      tripId = "";
    }
    // Set trip start time and date from tripStartTime
    Date tripStartTime = getTripStartTime(trip.getStopUpdates());
    if (tripStartTime != null) {
      DateFormat df = new SimpleDateFormat("HH:mm:ss");
      String startTime = df.format(tripStartTime);
      df = new SimpleDateFormat("yyyyMMdd");
      String startDate = df.format(tripStartTime);
      td.setStartTime(startTime);
      td.setStartDate(startDate);
      _log.debug("trip start time: " + startDate);
    } else {
      _log.debug("Null tripStartTime for trip " + tripId);
    }
    td.setTripId(tripId);
    td.setScheduleRelationship(ScheduleRelationship.SCHEDULED);
    td.setRouteId(_linkRouteId);
    _log.debug("Ending buildTripDescriptor");
    return td.build();
  }
  
  private void updateTripsAndStops() {
    // Check if date has changed.
    // If it has, update the trips ids so they are valid for this service date
    if ((new Date()).getTime() > timeToUpdateTripIds) {
      tripEntries = getLinkTrips();
      Calendar nextUpdate = Calendar.getInstance();  // Start with today
      nextUpdate.set(Calendar.HOUR_OF_DAY, 0);
      nextUpdate.set(Calendar.MINUTE, 0);
      nextUpdate.set(Calendar.SECOND, 0);
      nextUpdate.set(Calendar.MILLISECOND, 0);
      nextUpdate.add(Calendar.DATE, 1);   // Set to tomorrow at 3AM
      nextUpdate.add(Calendar.HOUR_OF_DAY, 3);
      timeToUpdateTripIds = nextUpdate.getTimeInMillis();
      SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
      _log.info("List of GTFS trips updated.  Will next be updated after " 
          + sdf.format(new Date(timeToUpdateTripIds)));
      updateStopOffsets();
    }
    
  }
  
  private List<TripEntry> getLinkTrips() {
    String routeId = _linkRouteId;
    List<TripEntry> allTrips = _transitGraphDao.getAllTrips();
    List<TripEntry> linkTrips = new ArrayList<TripEntry>();
    for (TripEntry trip : allTrips) {
      if (trip.getRoute().getId().getId().equals(routeId)) {
        // Check if this trip has a service id that is valid for today.
        BlockEntry blockEntry = trip.getBlock();
        List<BlockConfigurationEntry> bceList = blockEntry.getConfigurations();
        LocalizedServiceId serviceId = trip.getServiceId();
        Set<Date> activeDates = _calendarService.getDatesForServiceIds(new ServiceIdActivation(serviceId));
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        boolean isActiveToday = 
            _calendarService.areServiceIdsActiveOnServiceDate(
                new ServiceIdActivation(serviceId), today.getTime());
        if (isActiveToday) {
          linkTrips.add(trip);
        }
      }
    }
    TripComparator tripComparator = new TripComparator();
    Collections.sort(linkTrips, tripComparator);
    for (TripEntry trip : linkTrips) {
      _log.info("Trip " + trip.getId().toString());
    }
    
    return linkTrips;
  }

  private void updateStopOffsets() {
    // Create tables of northbound and southbound stop offsets
    int sbStopCt = 0;   // Direction = "0"
    int nbStopCt = 0;   // Direction = "1"
    TripEntry sbTrip = null;
    TripEntry nbTrip = null;
    for (TripEntry trip : tripEntries) {
      List<StopTimeEntry> stopTimeEntries = trip.getStopTimes();
      if (trip.getDirectionId().equals("0") && stopTimeEntries.size() > sbStopCt) {
        sbTrip = trip;
        sbStopCt = stopTimeEntries.size();
      } else if (trip.getDirectionId().equals("1") && stopTimeEntries.size() > nbStopCt) {
        nbTrip = trip;
        nbStopCt = stopTimeEntries.size();
      }
    }
    if (sbStopCt == 0) {
      _log.error("No southbound stops found for route "  + _linkRouteId);
    }
    if (nbStopCt == 0) {
      _log.error("No northbound stops found for route " + _linkRouteId);
    }
    _log.info("Southbound trip " + sbTrip.getId().toString() + " has "
        + sbStopCt + " stops.");
    _log.info("Northbound trip " + nbTrip.getId().toString() + " has "
        + nbStopCt + " stops.");
    List<StopTimeEntry> sbStopTimeEntries = sbTrip.getStopTimes();
    sbStopOffsets.clear();
    nbStopOffsets.clear();
    for (StopTimeEntry stopTimeEntry : sbStopTimeEntries) {
      String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
      String avlStopId = getAVLStopId(gtfsStopId);
      int arrivalTime = stopTimeEntry.getArrivalTime();
      _log.info("GTFS/AVL id: " + gtfsStopId + " / " + avlStopId);
      sbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, "0", arrivalTime));
    }
    List<StopTimeEntry> nbStopTimeEntries = nbTrip.getStopTimes();
    for (StopTimeEntry stopTimeEntry : nbStopTimeEntries) {
      String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
      String avlStopId = getAVLStopId(gtfsStopId);
      int arrivalTime = stopTimeEntry.getArrivalTime();
      _log.info("GTFS/AVL id: " + gtfsStopId + " / " + avlStopId);
      nbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, "1", arrivalTime));
    }
    if (sbStopOffsets.size() > 0 && nbStopOffsets.size() > 0) {
      Collections.sort(sbStopOffsets, new StopOffsetComparator());
      // Adjust offsets, setting first stop to zero and adjusting the others
      // accordingly.
      int offsetAdjustment = sbStopOffsets.get(0).getOffset();
      for (StopOffset so : sbStopOffsets) {
        so.setOffset(so.getOffset() - offsetAdjustment);
        _log.info(so.toString());
      }
      Collections.sort(nbStopOffsets, new StopOffsetComparator());
      // Adjust offsets, setting first stop to zero and adjusting the others
      // accordingly.
      offsetAdjustment = nbStopOffsets.get(0).getOffset();
      for (StopOffset so : nbStopOffsets) {
        so.setOffset(so.getOffset() - offsetAdjustment);
        _log.info(so.toString());
      }

    }
    return;
  }
  
  private StopTimeUpdate buildStopTimeUpdate(String stopId, String arrivalTime, 
      String direction, String scheduleRelationship) {
    StopTimeUpdate.Builder stu = StopTimeUpdate.newBuilder();
    try {
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
      Date parsedDate = df.parse(arrivalTime);
      StopTimeEvent.Builder ste = StopTimeEvent.newBuilder();
      ste.setTime(parsedDate.getTime() / 1000);
      if (stopId != null) {
        stopId = getGTFSStop(stopId, direction);
        if (stopId != null) {
          stu.setStopId(stopId);
          stu.setArrival(ste);
          stu.setDeparture(ste);
          if (scheduleRelationship.equals("SKIPPED")) {
            stu.setScheduleRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED);
          }
        }
      }
    } catch (Exception e) {
    _log.error("Exception parsing Estimated time " + arrivalTime + " for stop " + stopId);
    }
    return stu.build();
  }

  private String getTripForStop(String stopId, String direction, int scheduledTime) {
    int offset = 0;
    List<StopOffset> stopOffsets = null;
    if (direction.equals("0")) {
      stopOffsets = sbStopOffsets;
    } else {
      stopOffsets = nbStopOffsets;
    }
    for (StopOffset stopOffset : stopOffsets) {
      if (stopOffset.getGtfsStopId().equals(stopId)) {
        offset = stopOffset.getOffset();
        break;
      }
    }
    int tripStartTime = scheduledTime - offset;
    String tripId = "";
    TripEntry lastTrip = null;
    for (TripEntry gtfsTripEntry : tripEntries) {
      if (!direction.equals(gtfsTripEntry.getDirectionId())) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        }
        continue;
      }
      //int startTimeInSecs = getTripStartTimeInSecs(tripEntry);
      if (tripStartTime < getTripStartTimeInSecs(gtfsTripEntry)) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        } else {  // Earlier than the first entry, so just use the first stop.
          tripId = gtfsTripEntry.getId().getId();
        }
        break;
      }
      lastTrip = gtfsTripEntry;
    }
    if (tripId == null) {  // Assign it to the last trip
      tripId = tripEntries.get(tripEntries.size()-1).getId().getId();
    }
    return tripId;
  }
  
  // Returns the start time in seconds of the trip
  private int getTripStartTimeInSecs(TripEntry trip) {
  	int startTime = 0;
  	List<BlockConfigurationEntry> blocks = trip.getBlock().getConfigurations();
  	if (blocks.size() > 0) {
  		List<FrequencyEntry> frequencies = blocks.get(0).getFrequencies();
  		if (frequencies.size() > 0) {
  			startTime = frequencies.get(0).getStartTime();
  		}
  	}
  	return startTime;
  }
  
  /*
   * Generate dummy entries for any stops preceding the first real stop in the
   * AVL feed of stop updates for this trip.
   */
  private List<StopTimeUpdate> buildPseudoStopTimeUpdates(List<StopUpdate> stopUpdates,
      String dir) {
    List<StopTimeUpdate> dummyStopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    List<StopOffset> stopOffsets = (dir.equals("0")) ? sbStopOffsets : nbStopOffsets;
    int numberOfStops = stopOffsets.size();
    String firstRealStopId = stopUpdates.get(0).getStopId();
    int firstRealStopIdx = 0;
    
    // Make sure the first real stop is headed in the right direction,
    // since a train may come onto the line at a NB platform, for instance,
    // and immediately switch to the SB platform and continue SB for the
    // rest of the trip.
    String wrongDir = "SB";
    if (dir.equals("0")) {
      wrongDir = "NB";
    }
    if (firstRealStopId.startsWith(wrongDir)) {
      for (int i=0; i < stopUpdates.size(); i++) {
        if (!stopUpdates.get(i).getStopId().startsWith(wrongDir)) {
          firstRealStopId = stopUpdates.get(i).getStopId();
          firstRealStopIdx = i;
          _log.debug("Using firstRealStop of " + firstRealStopId);
          break;
        }
      }
    }
    
    // Set pseudoStopId to first stop on the route.
    String pseudoStopId = stopOffsets.get(0).getLinkStopId();
    
    ArrivalTime arrivalTimeDetails = stopUpdates.get(firstRealStopIdx).getArrivalTime();
    
    // Create pseudo entries from the beginning of the line to the first stop
    // in the stop updates.
    int idx = 0;
    while (!pseudoStopId.equals(firstRealStopId)) {
      String dummyArrivalTime = 
          getAdjustedTime(firstRealStopId, arrivalTimeDetails, pseudoStopId, dir);
      if (!dummyArrivalTime.isEmpty()) {
        StopTimeUpdate stopTimeUpdate = 
            buildStopTimeUpdate(pseudoStopId, dummyArrivalTime, dir, "SKIPPED");
      
        dummyStopTimeUpdateList.add(stopTimeUpdate);
      }
      pseudoStopId = stopOffsets.get(++idx).getLinkStopId();
    }

    
    return dummyStopTimeUpdateList;
  }

  /*
   * This method is used when creating pseudo entries for a trip covering only
   * part of the route to prevent OBA from creating its own entries for 
   * those stops.
   */
  private String getAdjustedTime(String baseStopId, 
      ArrivalTime arrivalTimeDetails, String targetStopId, String dir) {
    List<StopOffset> stopOffsets = (dir.equals("0")) ? sbStopOffsets : nbStopOffsets;
    String adjustedTime = "";
    if (arrivalTimeDetails != null) {
      int numberOfStops = stopOffsets.size();
      int idx = 0;
      int baseOffset = -1;
      int targetOffset = -1;
      // Run through the list of StopOffsets for this direction
      for (int i=0; i<numberOfStops; i++) {
        StopOffset stopOffset = stopOffsets.get(i);
        if (baseStopId.equals(stopOffset.getLinkStopId())) {
          baseOffset = stopOffset.getOffset();
        }
        if (targetStopId.equals(stopOffset.getLinkStopId())) {
          targetOffset = stopOffset.getOffset();
        }
      }
      if (baseOffset >= 0 && targetOffset >= 0) {
        String arrivalTime = arrivalTimeDetails.getActual();
        if (arrivalTime == null) {
          arrivalTime = arrivalTimeDetails.getEstimated();
        }
        if (arrivalTime != null) {
          try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
            Date parsedDate = df.parse(arrivalTime);
            int timeDelta = (targetOffset - baseOffset) * 1000;
            Date adjustedDate = new Date(parsedDate.getTime() + timeDelta);
            
            // If the adjusted time is in the future or less than two minutes
            // ago, reset it to five minutes ago so it won't generate a
            // bogus prediction.
            if ((System.currentTimeMillis() - 2 * 60 * 1000) 
                < adjustedDate.getTime()) {
              adjustedDate = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
            }
              
            adjustedTime = df.format(adjustedDate);
          } catch (Exception e) {
            _log.error("Exception parsing arrival time: " + arrivalTime);
          }
        }
      }
    }
    return adjustedTime;
  }

  private class TripComparator implements Comparator<TripEntry> {

    @Override
    public int compare(TripEntry t1, TripEntry t2) {
      // Compare direction
      if (!t1.getDirectionId().equals(t2.getDirectionId())) {
        if (t1.getDirectionId().equals("0")) {    // Outbound, to the airport
          return -1;
        } else {
          return 1;
        }
      }
      // Compare trip start times
      int time1 = getTripStartTimeInSecs(t1);
      int time2 = getTripStartTimeInSecs(t2);
      if (time1 != time2) {
        if (time1 < time2) {
          return -1;
        } else {
          return 1;
        }
      }
      return 0;
    }
  }
  
  private class StopOffsetComparator implements Comparator<StopOffset> {
    // Compare StopOffsets based on their offset values.
    @Override
    public int compare(StopOffset so1, StopOffset so2) {
      return so1.getOffset() - so2.getOffset();
    }
  }

  private String getTripDirection(TripInfo trip) {
    String direction = "";
    if (trip.getDirection() == null) {
      // No direction provided, so check for a stop starting with "NB" or "SB".
      String stopId = (String) trip.getLastStopId();
      if (trip.getStopUpdates() != null) {    // Check the StopIds in Updates
        StopUpdatesList stopTimeUpdateList = trip.getStopUpdates();
        if (stopTimeUpdateList.getUpdates() != null) {
          List<StopUpdate> stopTimeUpdates = stopTimeUpdateList.getUpdates();
          if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
            //for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
            // Check stop updates starting with the last, since direction
            // changes may occur at the beginning.
            for (int i=stopTimeUpdates.size()-1; i >= 0; --i) {
              if (stopTimeUpdates.get(i).getStopId() == null
                  || stopTimeUpdates.get(i).getStopId().isEmpty()
                  || stopTimeUpdates.get(i).getStopId().equals(PINE_STREET_STUB)) {
                continue;
              }          
              stopId = stopTimeUpdates.get(i).getStopId();
              if (stopId.startsWith("SB")) {
                direction = "0";
                break;
              } else if (stopId.startsWith("NB")) {
                direction = "1";
                break;
              }
            }
          }
        }
      }
    } else if (trip.getDirection().equals("N")) {
      direction = "1";
    } else if (trip.getDirection().equals("S")) {
      direction = "0";
    }
    
    if (direction.isEmpty()) {
      _log.info("No trip direction provided or inferred for trip " + trip.getTripId() + ". Defaulting to outbound.");
      direction = "0";     // Default to south, outbound (southbound)
    }

    return direction;
  }

  private String getGTFSStop(String stopId, String direction) {
    String mappedStopId = "";
    if (stopId != null) {
      mappedStopId = stopMapping.get(stopId);
    }

    if (mappedStopId != null && !mappedStopId.isEmpty()) {
      // Check for special case at Sea-Tac Airport, where both northbound 
      // and southbound trains have an AVL stop id of "SEA_PLAT".
      if (mappedStopId.equals("99903") || mappedStopId.equals("99904")) {
        if (direction == "0") {
          mappedStopId = "99904";
        } else {
          mappedStopId = "99903";
        }
      }
    }
    return mappedStopId;
  }
  
  private String getAVLStopId(String gtfsStopId) {
    String result = "";
    if (gtfsStopId.equals("99903") || gtfsStopId.equals("99904")) {
      result = "SEA_PLAT";
    } else {
      Iterator it = stopMapping.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> stopPair = (Map.Entry)it.next();
        if (stopPair.getValue().equals(gtfsStopId)) {
          result = stopPair.getKey();
          break;
        }
      }
    }

    return result;
  }
}
