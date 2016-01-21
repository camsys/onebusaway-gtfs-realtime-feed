/**
 * Copyright (C) 2015 Cambridge Systematics, Inc.
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
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
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
  private static String SEA_TAC = "SEA_PLAT";
  private static String PINE_STREET_STUB = "PNSS_PLAT"; // Not really a stop.
  private TransitGraphDao _transitGraphDao;
  private Map<String, String> stopMapping = null;
  //private Map<String, String> tripMapping = null;
  private String _linkAgencyId;
  private static String _linkRouteId;
  private static String _linkStopMappingFile;
  private FeedMessage _currentVehiclePositions;
  private FeedMessage _currentTripUpdates;
  private List<TripEntry> _tripEntries;
  private List<StopOffset> stopOffsets;

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

  public void set_tripEntries(List<TripEntry> _tripEntries) {
    this._tripEntries = _tripEntries;
  }

  public Map<String, String> getStopMapping() {
    if (stopMapping == null) {
      BufferedReader br = null;
      try {
        stopMapping = new HashMap<String, String>();
        String ln = "";
        if (_linkStopMappingFile == null || _linkStopMappingFile.isEmpty()) {
          _linkStopMappingFile = "/var/lib/oba/LinkStopMapping.txt";  // Default
        }
        br = new BufferedReader(new FileReader(_linkStopMappingFile));
        while ((ln = br.readLine()) != null) {
          int idx = ln.indexOf(',');
          if (idx > 0) {
            stopMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
          }
        }
      } catch (IOException e) {
        _log.error("Error reading StopMapping file " + e.getMessage());
      } finally {
        try {
          if (br != null) {
            br.close();
          }
        } catch (IOException e) {
          _log.error("Exception closing file reader: " + e.getMessage());
        }
      }
    }
    return stopMapping;
  }

  public void setStopMapping(Map<String, String> stopMapping) {
    this.stopMapping = stopMapping;
  }

  public void init() {
	  if (_tripEntries == null) {  // Should be null unless unit test injects a value.
	    _tripEntries = getLinkTrips();
	  }
	  // Set up list of offset for stops.  This is the time in minutes from the beginning of the trip
	  // For now hardcode the tables to help localize debugging.
	  // TBD: construct the tables dynamically
	  stopOffsets = new ArrayList<StopOffset>();
	  // Add in Southbound stops
	  stopOffsets.add(new StopOffset("1108", "SB1029T", "0", 0));
    stopOffsets.add(new StopOffset("455", "SB1047T", "0", 2));
    stopOffsets.add(new StopOffset("501", "SB1070T", "0", 4));
    stopOffsets.add(new StopOffset("623", "SB1087T", "0", 7));
    stopOffsets.add(new StopOffset("99101", "SB113T", "0", 9));
    stopOffsets.add(new StopOffset("99111", "SB148T", "0", 11));
    stopOffsets.add(new StopOffset("99121", "SB210T", "0", 14));
    stopOffsets.add(new StopOffset("55949", "SB255T", "0", 16));
    stopOffsets.add(new StopOffset("56039", "SB312T", "0", 19));
    stopOffsets.add(new StopOffset("56159", "SB417T", "0", 23));
    stopOffsets.add(new StopOffset("56173", "SB469T", "0", 26));
    stopOffsets.add(new StopOffset("99900", "SB778T", "0", 35));
    stopOffsets.add(new StopOffset("99904", "SEA_PLAT", "0", 38));
	  
	  // Add in Northbound stops
    stopOffsets.add(new StopOffset("99903", "SEA_PLAT", "1", 0));
    stopOffsets.add(new StopOffset("99905", "NB782T", "1", 3));
    stopOffsets.add(new StopOffset("55578", "NB484T", "1", 12));
    stopOffsets.add(new StopOffset("55656", "NB435T", "1", 15));
    stopOffsets.add(new StopOffset("55778", "NB331T", "1", 19));
    stopOffsets.add(new StopOffset("55860", "NB260T", "1", 22));
    stopOffsets.add(new StopOffset("99240", "NB215T", "1", 24));
    stopOffsets.add(new StopOffset("99256", "NB153T", "1", 27));
    stopOffsets.add(new StopOffset("99260", "NB117T", "1", 29));
    stopOffsets.add(new StopOffset("621", "NB1093T", "1", 31));
    stopOffsets.add(new StopOffset("532", "NB1075T", "1", 34));
    stopOffsets.add(new StopOffset("565", "NB1053T", "1", 36));
    stopOffsets.add(new StopOffset("1121", "NB1036T", "1", 38));
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
      _log.error("JsonMappingException trying to parse feed data.");
      parseFailed = true;
    } catch (IOException e) {
      _log.error("IOException trying to parse feed data.");
      parseFailed = true;
    }
    if (parseFailed) {
      return null;
    }
    // The AVL feed occasionally has dates from 1899.  That apparently is some
    // sort of default value for their system.
    // Convert any "1899" dates in ArrivalTime to today
    TripInfoList tripInfoList = linkAVLData.getTrips();
    List<TripInfo> trips = tripInfoList.getTrips();
    for (TripInfo trip : trips) {
      StopUpdatesList stopUpdatesList = trip.getStopUpdates();
      List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
      if (stopUpdates != null && stopUpdates.size() > 0) {
        for (StopUpdate stopTimeUpdate : stopUpdates) {
          ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
          if (arrivalTime != null) {
            String actual = arrivalTime.getActual();
            String estimated = arrivalTime.getEstimated();
            String scheduled = arrivalTime.getScheduled();
            if (actual != null && actual.startsWith("1899")) {
              actual = convert1899Date(actual);
              arrivalTime.setActual(actual);
            }
            if (estimated != null && estimated.startsWith("1899")) {
              estimated = convert1899Date(estimated);
              arrivalTime.setEstimated(estimated);
            }
            if (scheduled != null && scheduled.startsWith("1899")) {
              scheduled = convert1899Date(scheduled);
              arrivalTime.setScheduled(scheduled);
            }
          }
        }
      }
    }
    
    return linkAVLData;
  }
  
  private String convert1899Date(String oldDateString) {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    String today = formatter.format(new Date());
    String todaysDateString = today 
        + oldDateString.substring(oldDateString.indexOf("T"));
    return todaysDateString;
  }

  public FeedMessage buildVPMessage(LinkAVLData linkAVLData) {
    int vehiclePositionEntityCount = 0;
    FeedMessage vehiclePositionsFM = null;
    TripInfoList tripInfoList = linkAVLData.getTrips();
    List<TripInfo> trips = tripInfoList.getTrips();
    FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
    FeedHeader.Builder header = FeedHeader.newBuilder();
    header.setTimestamp(System.currentTimeMillis()/1000);
    header.setIncrementality(Incrementality.FULL_DATASET);
    header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
    feedMessageBuilder.setHeader(header);
    if (trips != null) {
      for (TripInfo trip : trips) {
        // If there are no stop time updates, don't generate a VehiclePosition
        // for this trip.
        int stopUpdateCt = trip.getStopUpdates().getUpdates().size();
        if (stopUpdateCt < 2) {
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

        // Loop through StopUpdates to determine the trip start time and date.
        // Initially, set nextStopTime to an arbitrarily high value.
        Calendar cal = Calendar.getInstance();
        cal.set(2099, 12, 31);
        TripDescriptor td = buildTripDescriptor(trip);
        vp.setTrip(td);

        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(vehicleId);
        entity.setVehicle(vp);
        feedMessageBuilder.addEntity(entity);
        ++vehiclePositionEntityCount;
      }
    } else {
      // TODO: decide what to do if no data is found.
    }
    vehiclePositionsFM = feedMessageBuilder.build();
    if (vehiclePositionsFM != null) {
      _currentVehiclePositions = vehiclePositionsFM;
    } else {
      vehiclePositionsFM = FeedMessage.getDefaultInstance();
    }
    _log.info("vehicle position updates: " + vehiclePositionEntityCount);
    return vehiclePositionsFM;
  }

  public FeedMessage buildTUMessage(LinkAVLData linkAVLData) {
    FeedMessage tripUpdatesFM = null;
    TripInfoList tripsData = linkAVLData.getTrips();
    List<TripInfo> trips = null;
    if (tripsData != null) {
      trips = tripsData.getTrips();
    }
    FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
    FeedHeader.Builder header = FeedHeader.newBuilder();
    header.setTimestamp(System.currentTimeMillis()/1000);
    header.setIncrementality(Incrementality.FULL_DATASET);
    header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
    feedMessageBuilder.setHeader(header);
    _log.debug("Processing " + trips.size() + " trips");
    if (trips != null) {
      for (TripInfo trip : trips) {
        TripUpdate.Builder tu = TripUpdate.newBuilder();
        // If there are no stop time updates, don't generate a TripUpdate
        // for this trip.
        int stopUpdateCt = trip.getStopUpdates().getUpdates().size();
        if (stopUpdateCt < 2) {
          continue;
        }
        // Build the StopTimeUpdates
        List<StopTimeUpdate> stopTimeUpdates = buildStopTimeUpdateList(trip);
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
      }
    } else {
      // TODO: decide what to do if no data is found.
      // Map<String, String> feedResult = (Map<String, String>)
      // parsedAvlUpdates.get("Fault");
    }
    tripUpdatesFM = feedMessageBuilder.build();
    if (tripUpdatesFM != null) {
      _currentTripUpdates = tripUpdatesFM;
    } else {
      tripUpdatesFM = FeedMessage.getDefaultInstance();
    }
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
            || stopTimeUpdate.getStopId().equals(PINE_STREET_STUB)) {
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
        //StopTimeUpdate.Builder stu = StopTimeUpdate.newBuilder();
        ArrivalTime arrivalTimeDetails = stopTimeUpdate.getArrivalTime();
        String scheduledArrival = null;
        if (arrivalTimeDetails != null) {
          scheduledArrival = arrivalTimeDetails.getScheduled();
        }
        //DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
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
      List<StopUpdate> modStopUpdates = new ArrayList<>();
      for (int i=0; i<stopUpdates.size(); ++i) {
        String stopId = stopUpdates.get(i).getStopId();
        if (!stopId.equals(PINE_STREET_STUB)) {
          modStopUpdates.add(stopUpdates.get(i));
        }
      }
      stopUpdates = modStopUpdates;
      
      // For testing partial routes
      /*
      // Drop the last two StopTimeUpdates
      _log.info("Full StopUpdates size: " + stopUpdates.size());
      if (stopUpdates.size() >= 3) {
        List<StopUpdate> testStopUpdates = new ArrayList<StopUpdate>();
        for (int i=0; i<stopUpdates.size()-2; ++i) {
          testStopUpdates.add(stopUpdates.get(i));
        }
        stopUpdates = testStopUpdates;
        _log.info("Modified StopUpdates size: " + stopUpdates.size());
      }
      */
      // End test block
      
      // For trips that serve less than the full route between Westlake
      // and Sea-Tac, generate dummy entries for stops that would have already
      // been seen if this trip were serving the full route.
      /* */
      String firstSBStop = stopOffsets.get(0).getLinkStopId();
      String firstNBStop = stopOffsets.get(stopOffsets.size()/2).getLinkStopId();
      if (stopUpdates.size() < stopOffsets.size()/2
          && !stopUpdates.get(0).getStopId().equals(firstSBStop)
          && !stopUpdates.get(0).getStopId().equals(firstNBStop)) {
        _log.info("Prepending pseudo stop time updates for trip " + trip.getTripId());
        List<StopTimeUpdate> dummyStopTimeUpdates = 
            buildDummyStopTimeUpdates(stopUpdates, getTripDirection(trip));
        stopTimeUpdateList.addAll(dummyStopTimeUpdates);
      }
      /* */
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
          if (arrivalTime != null) {
            StopTimeUpdate stopTimeUpdate = 
                buildStopTimeUpdate(stopUpdate.getStopId(),
                    arrivalTime, getTripDirection(trip), "");
            stopTimeUpdateList.add(stopTimeUpdate);
          }
        }
      }
      // Check if stopUpdates need to be appended to the StopUpdateList
      String lastSBStop = stopOffsets.get(stopOffsets.size()/2-1).getLinkStopId();
      String lastNBStop = stopOffsets.get(stopOffsets.size()-1).getLinkStopId();
      _log.debug("Number of stop updates: " + stopUpdates.size());
      _log.debug("Total number of stops: " + stopOffsets.size()/2);
      if (stopUpdates.size() < stopOffsets.size()/2
          && !stopUpdates.get(stopUpdates.size()-1).getStopId().equals(lastSBStop)
          && !stopUpdates.get(stopUpdates.size()-1).getStopId().equals(lastNBStop)) {
        _log.info("Appending stop time updates for trip " + trip.getTripId());
        List<StopTimeUpdate> appendedStopTimeUpdates = 
            buildAppendedStopTimeUpdates(stopUpdates);
        stopTimeUpdateList.addAll(appendedStopTimeUpdates);
      }
    }
    return stopTimeUpdateList;
  }

  private TripDescriptor buildTripDescriptor(TripInfo trip) {
    TripDescriptor.Builder td = TripDescriptor.newBuilder();
    String stopId = "";
    int scheduledTime = 0;
    StopUpdatesList stopTimeUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData.getUpdates();
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
    _log.debug("Stop id before mapping: " + stopId);
    String mappedStopId = null;
    if (stopId != null) {
      String direction = getTripDirection(trip);
      mappedStopId = getGTFSStop(stopId, direction);
    }

    if (mappedStopId == null || mappedStopId.isEmpty()) {
      _log.info("mappedStopId is null for trip " + trip.getTripId() + " and stop " + trip.getLastStopName() 
          + ", stopid is " + stopId);
    }
    String direction = getTripDirection(trip);
    String tripId = getTripForStop(mappedStopId, direction, scheduledTime);
    if (tripId == null) {
      _log.info("trip id is null");
      tripId = "";
    }
    // Set trip start time and date from tripStartTime
    Date tripStartTime = getTripStartTime(trip.getStopUpdates());
    if (tripStartTime != null) {
      DateFormat df = new SimpleDateFormat("kk:mm:ss");
      String startTime = df.format(tripStartTime);
      df = new SimpleDateFormat("yyyyMMdd");
      String startDate = df.format(tripStartTime);
      td.setStartTime(startTime);
      td.setStartDate(startDate);
    } else {
      _log.info("Null tripStartTime for trip " + tripId);
    }
    td.setTripId(tripId);
    td.setScheduleRelationship(ScheduleRelationship.SCHEDULED);
    td.setRouteId(_linkRouteId);
    return td.build();
  }
  
  private List<TripEntry> getLinkTrips() {
    String routeId = _linkRouteId;
    List<TripEntry> allTrips = _transitGraphDao.getAllTrips();
    List<TripEntry> linkTrips = new ArrayList<TripEntry>();
    for (TripEntry trip : allTrips) {
      //if (trip.getRoute().getId().getId().equals(routeId) && trip.getBlock().getId().getId().equals(trip.getId().getId())) {
      if (trip.getRoute().getId().getId().equals(routeId)) {
        linkTrips.add(trip);
      }
    }
    TripComparator tripComparator = new TripComparator();
    Collections.sort(linkTrips, tripComparator);
    
    return linkTrips;
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
    _log.error("Exception parsing Estimated time: " + arrivalTime);
    }
    return stu.build();
  }

  private String getTripForStop(String stopId, String direction, int scheduledTime) {
    int offset = 0;
    for (StopOffset stopOffset : stopOffsets) {
      if (stopOffset.getGtfsStopId().equals(stopId)) {
        offset = stopOffset.getOffset();
        break;
      }
    }
    int tripStartTime = scheduledTime - (offset * 60);
    String tripId = "";
    TripEntry lastTrip = null;
    for (TripEntry tripEntry : _tripEntries) {
      if (!direction.equals(tripEntry.getDirectionId())) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        }
        continue;
      }
      //int startTimeInSecs = getTripStartTimeInSecs(tripEntry);
      if (tripStartTime < getTripStartTimeInSecs(tripEntry)) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        } else {  // Earlier than the first entry, so just use the first stop.
          tripId = tripEntry.getId().getId();
        }
        break;
      }
      lastTrip = tripEntry;
    }
    if (tripId == null) {  // Assign it to the last stop
      tripId = _tripEntries.get(_tripEntries.size()-1).getId().getId();
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
  private List<StopTimeUpdate> buildDummyStopTimeUpdates(List<StopUpdate> stopUpdates,
      String dir) {
    List<StopTimeUpdate> dummyStopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    
    int numberOfStops = stopOffsets.size()/2;
    String lastSouthboundStopId = stopOffsets.get(numberOfStops - 1).getLinkStopId();
    String lastNorthboundStopId = stopOffsets.get(stopOffsets.size()-1).getLinkStopId();
    String firstRealStopId = stopUpdates.get(0).getStopId();
    String lastRealStopId = stopUpdates.get(stopUpdates.size()-1).getStopId();
      
    int idx = dir.equals("0") ? 0 : stopOffsets.size()/2;
    String dummyStopId = stopOffsets.get(idx).getLinkStopId();
    
    ArrivalTime arrivalTimeDetails = stopUpdates.get(0).getArrivalTime();
    
    while (!dummyStopId.equals(firstRealStopId)) {
      String dummyArrivalTime = 
          getAdjustedTime(stopUpdates.get(0), dummyStopId, dir);
      
      StopTimeUpdate stopTimeUpdate = 
          buildStopTimeUpdate(dummyStopId, dummyArrivalTime, dir, "SKIPPED");
      
      // Set ScheduleRelationship
      
      dummyStopTimeUpdateList.add(stopTimeUpdate);
      dummyStopId = stopOffsets.get(++idx).getLinkStopId();
    }

    
    return dummyStopTimeUpdateList;
  }
  
  private List<StopTimeUpdate> buildAppendedStopTimeUpdates(List<StopUpdate> stopUpdates) {
    List<StopTimeUpdate> dummyStopTimeUpdateList = new ArrayList<StopTimeUpdate>();

    int numberOfStops = stopOffsets.size()/2;
    String lastSouthboundStopId = stopOffsets.get(numberOfStops - 1).getLinkStopId();
    String lastNorthboundStopId = stopOffsets.get(stopOffsets.size()-1).getLinkStopId();
    String firstRealStopId = stopUpdates.get(0).getStopId();
    String lastRealStopId = stopUpdates.get(stopUpdates.size()-1).getStopId();
    
    String dir = firstRealStopId.startsWith("SB") ? "0" : "1";
    int lastStopIdx = dir.equals("0") ? (stopOffsets.size()/2) - 1 : stopOffsets.size() - 1;
    //String lastStopId = stopUpdates.get(lastStopIdx).getStopId();
    String lastStopId = dir.equals("0") ? lastSouthboundStopId : lastNorthboundStopId;
    int entriesToAdd = numberOfStops - stopUpdates.size();
    
    int idx = dir.equals("0") ? 0 : stopOffsets.size()/2;
    idx += stopUpdates.size();
    
    
    
    for (int i=0; i<entriesToAdd; ++i) {
      String dummyStopId = stopOffsets.get(idx++).getLinkStopId();
      String dummyArrivalTime = 
          getAdjustedTime(stopUpdates.get(stopUpdates.size()-1), dummyStopId, dir);
      
      StopTimeUpdate stopTimeUpdate = 
          buildStopTimeUpdate(dummyStopId, dummyArrivalTime, dir, "SKIPPED");
      
      dummyStopTimeUpdateList.add(stopTimeUpdate);
    }

    
    return dummyStopTimeUpdateList;
  }
  
  private String getAdjustedTime(StopUpdate baseStopUpdate, String targetStopId, String dir) {
    String adjustedTime = "";
    ArrivalTime arrivalTimeDetails = baseStopUpdate.getArrivalTime();
    if (arrivalTimeDetails != null) {
      int numberOfStops = stopOffsets.size()/2;
      int idx = dir.equals("0") ? 0 : stopOffsets.size()/2;
      int baseOffset = -1;
      int targetOffset = -1;
      for (int i=0; i<numberOfStops; i++) {
        StopOffset stopOffset = stopOffsets.get(idx+i);
        if (baseStopUpdate.getStopId().equals(stopOffset.getLinkStopId())) {
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
            int timeDelta = (targetOffset - baseOffset) * 60 * 1000;
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
  
  private class StopOffset {
    private String gtfsStopId;
    private String linkStopId;
    private String direction;
    private int offset;
    
    public String getGtfsStopId() {
      return gtfsStopId;
    }
    public void setGtfsStopId(String gtfsStopId) {
      this.gtfsStopId = gtfsStopId;
    }
    public String getLinkStopId() {
      return linkStopId;
    }
    public void setLinkStopId(String linkStopId) {
      this.linkStopId = linkStopId;
    }
    public String getDirection() {
      return direction;
    }
    public void setDirection(String direction) {
      this.direction = direction;
    }
    public int getOffset() {
      return offset;
    }
    public void setOffset(int offset) {
      this.offset = offset;
    }
    
    public StopOffset(String gtfsStopId, String linkStopId, String direction, int offset) {
      this.gtfsStopId = gtfsStopId;
      this.linkStopId = linkStopId;
      this.direction = direction;
      this.offset = offset;
    }
  }

  private String getTripDirection(TripInfo trip) {
    String direction = "";
    if (trip.getDirection() == null) {
      // No direction provided, so check for a stop starting with "NB" or "SB".
      String stopId = (String) trip.getLastStopId();
      if (stopId != null) {
        if (stopId.startsWith("SB")) {
          direction = "0";
        } else if (stopId.startsWith("NB")) {
          direction = "1";
        }
      }
      if (direction.isEmpty()) {    // Check the StopIds in Updates
        StopUpdatesList stopTimeUpdateList = trip.getStopUpdates();
        List<StopUpdate> stopTimeUpdates = stopTimeUpdateList.getUpdates();
        if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
          // Check if the only stop listed is the airport.  If so,
          // assume it's going to head inbound to Seattle.
          if (stopTimeUpdates.size() == 1 
              && stopTimeUpdates.get(0).getStopId().equals(SEA_TAC)) {
            direction = "1";
          } else {
            for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
              if (stopTimeUpdate.getStopId() == null
                  || stopTimeUpdate.getStopId().isEmpty()
                  || stopTimeUpdate.getStopId().equals(PINE_STREET_STUB)) {
                continue;
              }          
              stopId = stopTimeUpdate.getStopId();
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
      direction = "0";     // Default to south, outbound
    }

    return direction;
  }

  private String getGTFSStop(String stopId, String direction) {
    String mappedStopId = "";
    if (stopId != null) {
      mappedStopId = getStopMapping().get(stopId);
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
}
