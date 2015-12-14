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
import java.net.URL;
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
import org.onebusaway.transit_data_federation.services.transit_graph.AgencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.onebusaway.util.services.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
  private TransitGraphDao _transitGraphDao;
  private Map<String, String> stopMapping = null;
  private Map<String, String> tripMapping = null;

  //private static final String DEFAULT_LINK_ROUTE_ID = "100479";
  private String _linkAgencyId;
  private static String _linkRouteId;
  private static String _linkStopMappingFile;
  private FeedMessage _currentVehiclePositions;
  private FeedMessage _currentTripUpdates;
  //private String _linkRouteId;
  private List<TripEntry> tripEntries;
  private List<StopOffset> stopOffsets;
  //private ConfigurationService configService;

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

  public Map<String, String> getStopMapping() {
    if (stopMapping == null) {
      BufferedReader br = null;
      try {
        stopMapping = new HashMap<String, String>();
        String ln = "";
        //br = new BufferedReader(new FileReader(
        //    "/var/lib/oba/AvlStopMapping.txt"));
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
          br.close();
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

  public Map<String, String> getTripMapping() {
    if (tripMapping == null) {
      _log.info("Reading from AvlTripMappingFile");
      BufferedReader br = null;
      try {
        tripMapping = new HashMap<String, String>();
        String ln = "";
        br = new BufferedReader(new FileReader(
            "/var/lib/obanyc/gtfs-rt/AvlTripMapping.txt"));
        while ((ln = br.readLine()) != null) {
          int idx = ln.indexOf(',');
          if (idx > 0) {
            tripMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
          }
        }
      } catch (IOException e) {
        _log.error("Error reading TripMapping file " + e.getMessage());
      } finally {
        try {
          br.close();
        } catch (IOException e) {
          _log.error("Exception closing file reader: " + e.getMessage());
        }
      }
    }
    return tripMapping;
  }

  public void setTripMapping(Map<String, String> tripMapping) {
    this.tripMapping = tripMapping;
  }

  //public String getLinkRouteId() {
    //if (linkRouteId == null) {
    //  linkRouteId = configService.getConfigurationValueAsString(
    //      "linkRouteId", DEFAULT_LINK_ROUTE_ID);
    //}
    //return _linkRouteId;
  //}

  //public void setLinkRouteId(String linkRouteId) {
  //  this._linkRouteId = linkRouteId;
  //}

 // @Autowired
 // public void setConfigurationService(ConfigurationService configService) {
 //   this.configService = configService;
 // }

  public void init() {
	  _log.info("Starting _feedService.init()");
	  List<AgencyEntry> agencies = _transitGraphDao.getAllAgencies();
	  tripEntries = getLinkTrips();
	  // Set up list of offset for stops.  This is the time in minutes from the beginning of the trip
	  // For now hardcode the tables to help localize debugging.
	  // TBD: construct the tables dynamically
	  stopOffsets = new ArrayList<StopOffset>();
	  // Add in Southbound stops
	  stopOffsets.add(new StopOffset("1108", "0", 0));
    stopOffsets.add(new StopOffset("455", "0", 2));
    stopOffsets.add(new StopOffset("501", "0", 4));
    stopOffsets.add(new StopOffset("623", "0", 7));
    stopOffsets.add(new StopOffset("99101", "0", 9));
    stopOffsets.add(new StopOffset("99111", "0", 11));
    stopOffsets.add(new StopOffset("99121", "0", 14));
    stopOffsets.add(new StopOffset("55949", "0", 16));
    stopOffsets.add(new StopOffset("56039", "0", 19));
    stopOffsets.add(new StopOffset("56159", "0", 23));
    stopOffsets.add(new StopOffset("56173", "0", 26));
    stopOffsets.add(new StopOffset("99900", "0", 35));
    stopOffsets.add(new StopOffset("99904", "0", 38));
	  
	  // Add in Northbound stops
    stopOffsets.add(new StopOffset("99903", "1", 0));
    stopOffsets.add(new StopOffset("99905", "1", 3));
    stopOffsets.add(new StopOffset("55578", "1", 12));
    stopOffsets.add(new StopOffset("55656", "1", 15));
    stopOffsets.add(new StopOffset("55778", "1", 19));
    stopOffsets.add(new StopOffset("55860", "1", 22));
    stopOffsets.add(new StopOffset("99240", "1", 24));
    stopOffsets.add(new StopOffset("99256", "1", 27));
    stopOffsets.add(new StopOffset("99260", "1", 29));
    stopOffsets.add(new StopOffset("621", "1", 31));
    stopOffsets.add(new StopOffset("532", "1", 34));
    stopOffsets.add(new StopOffset("565", "1", 36));
    stopOffsets.add(new StopOffset("1121", "1", 38));
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
        _log.info("Parsed AVL data: " + mapper.writeValueAsString(linkAVLData));
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

    return linkAVLData;
  }

  public FeedMessage buildVPMessage(LinkAVLData linkAVLData) {
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
        VehiclePosition.Builder vp = VehiclePosition.newBuilder();
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        Position.Builder positionBuilder = Position.newBuilder();
        String vehicleId = (String) trip.getVehicleId();
        if (vehicleId == null) {
          vehicleId = "";
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

        String stopId = (String) trip.getLastStopId();
        if (stopId == null) {
          stopId = "";
        } else {
          stopId = getStopMapping().get(stopId);
          if (stopId == null) {
            _log.info("Could not map stop: " + (String) trip.getLastStopId());
            stopId = "";
          }
        }
        vp.setStopId(stopId);
        vp.setTimestamp(System.currentTimeMillis()/1000);
        vp.setCurrentStatus(VehiclePosition.VehicleStopStatus.INCOMING_AT);

        String nextStop = findNextStopOnTrip(trip);
        // Loop through StopUpdates to determine the trip start time and date.
        // Initially, set nextStopTime to an arbitrarily high value.
        Calendar cal = Calendar.getInstance();
        cal.set(2099, 12, 31);
        Date nextStopTime = cal.getTime();
        Date tripStartTime = null;
        StopUpdatesList stopTimeUpdateList = trip.getStopUpdates();
        List<StopUpdate> stopTimeUpdates = stopTimeUpdateList.getUpdates();
        if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
          for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
            StopTimeUpdate.Builder stu = StopTimeUpdate.newBuilder();
            ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
            String arrival = null;
            if (arrivalTime != null) {
              arrival = arrivalTime.getActual();
            }
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            if (arrival != null) { // If this is the earliest time, use it for
                                   // the trip start time
              Date parsedDate = null;
              try {
                parsedDate = df.parse(arrival);
              } catch (Exception e) {
                System.out.println("Exception parsing Estimated time: "
                    + arrival);
              }
              if (tripStartTime == null) {
                tripStartTime = parsedDate;
              } else if (parsedDate.before(tripStartTime)) {
                tripStartTime = parsedDate;
              }
            }
          }
        }
        // Reset stop id to next stop if it was unmapped
        if (stopId == "") {
          if (getStopMapping().get(nextStop) != null) {
            vp.setStopId(getStopMapping().get(nextStop));
          }
        }
        TripDescriptor td = buildTripDescriptor(trip);
        vp.setTrip(td);

        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(vehicleId);
        entity.setVehicle(vp);
        feedMessageBuilder.addEntity(entity);
      }
    } else {
      // TODO: decide what to do if no data is found.
      // Map<String, String> feedResult = (Map<String, String>)
      // parsedAvlUpdates.get("Fault");
    }
    vehiclePositionsFM = feedMessageBuilder.build();
    if (vehiclePositionsFM != null) {
      _currentVehiclePositions = vehiclePositionsFM;
    } else {
      vehiclePositionsFM = FeedMessage.getDefaultInstance();
    }
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
    _log.info("Process trips");
    if (trips != null) {
      for (TripInfo trip : trips) {
        TripUpdate.Builder tu = TripUpdate.newBuilder();
        // Build the StopTimeUpdates
        List<StopTimeUpdate> stopTimeUpdates = buildStopTimeUpdates(trip);
        tu.addAllStopTimeUpdate(stopTimeUpdates);

        // Build the VehicleDescriptor
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        String vehicleId = trip.getVehicleId();
        if (vehicleId == null) {
          vehicleId = "";
        }
        vd.setId(vehicleId);
        tu.setVehicle(vd);

        // Build the TripDescriptor
        TripDescriptor td = buildTripDescriptor(trip);
        tu.setTrip(td);
        tu.setTimestamp(System.currentTimeMillis()/1000);
        FeedEntity.Builder entity = FeedEntity.newBuilder();
        // Use VehicleId for entity Id since that is unique per trip
        entity.setId(trip.getVehicleId());
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

  private String findNextStopOnTrip(TripInfo trip) {
    // Check the times for the StopUpdates to determine which stop the vehicle
    // will reach next. That will be the stop with the earliest estimated
    // arrival time, but an actual time of null. If the trip is already
    // completed, i.e. every stop update has an actual arrival time, then an
    // empty string will be returned.
    // trip = (trip == null ? Collections.EMPTY_MAP : trip); //Check for null
    String nextStop = "";
    // Initially, set nextStopTime to an arbitrarily high value.
    Calendar cal = Calendar.getInstance();
    cal.set(2099, 12, 31);
    Date nextStopTime = cal.getTime();
    StopUpdatesList stopUpdatesList = trip.getStopUpdates();
    List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
    if (stopUpdates != null && stopUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopUpdates) {
        ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
        if (arrivalTime == null)
          continue;
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
            nextStop = stopTimeUpdate.getStopId();
          }
        }
      }
    }
    // _log.info("Finished findNextStopOnTrip");

    return nextStop;
  }

  private List<StopTimeUpdate> buildStopTimeUpdates(TripInfo trip) {
    List<StopTimeUpdate> stopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    StopUpdatesList stopTimeUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData.getUpdates();
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        StopTimeUpdate.Builder stu = StopTimeUpdate.newBuilder();
        ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
        String arrival = null;
        if (arrivalTime != null) {
          arrival = arrivalTime.getActual();
        }
        // If "Actual" is null, the stop hasn't happened yet, so use the
        // "Estimated" time.
        if (arrival == null && arrivalTime != null) {
          arrival = arrivalTime.getEstimated();
        }
        if (arrival != null) {
          try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
            Date parsedDate = df.parse(arrival);
            StopTimeEvent.Builder ste = StopTimeEvent.newBuilder();
            ste.setTime(parsedDate.getTime() / 1000);

            String stopId = stopTimeUpdate.getStopId();
            if (stopId != null) {
              stopId = getStopMapping().get(stopId);
              if (stopId == null) {
                continue; // No mapping for this stop, so don't add it.
              }
              stu.setStopId(stopId);
              stu.setArrival(ste);
              stu.setDeparture(ste);
            }
          } catch (Exception e) {
            _log.error("Exception parsing Estimated time: " + arrival);
          }
        }
        stopTimeUpdateList.add(stu.build());
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
        if (!stopTimeUpdate.getStopId().isEmpty()) {
          stopId = stopTimeUpdate.getStopId();
          String formattedTime = stopTimeUpdate.getArrivalTime().getScheduled();
          formattedTime = formattedTime.substring(formattedTime.indexOf('T')+1, formattedTime.indexOf('T') + 9);
          String[] timeArray = formattedTime.split(":");
          int hours = Integer.parseInt(timeArray[0]);
          int minutes = Integer.parseInt(timeArray[1]);
          scheduledTime = (hours * 60 + minutes) * 60;
          break;
        }
      }
    }
    
    if (stopId != null) {
      stopId = getStopMapping().get(stopId);
    }

    //String direction = getTripDirection(trip);
    String direction = "0"; 		// Default to south, outbound
    if (trip.getDirection().equals("N")) {
    	direction = "1";
    }
    String tripId = getTripForStop(stopId, direction, scheduledTime);
    if (tripId == null) {
      _log.info("trip id is null");
      tripId = "";
    }
    /*
     * else { _log.info("Mapping trip: " + tripId); tripId =
     * getTripMapping().get(tripId); // Set unmapped trips to the default trip
     * id if (tripId == null) { _log.info("Could not map trip " +
     * trip.get("TripId") + ". Defaulting to trip " + DEFAULT_TRIP_ID); tripId =
     * DEFAULT_TRIP_ID; } }
     */
    // Set trip start time and date from tripStartTime
    Date tripStartTime = getTripStartTime(trip);
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
    td.setScheduleRelationship(ScheduleRelationship.UNSCHEDULED);
    td.setRouteId(_linkRouteId);

    return td.build();
  }

  private Date getTripStartTime(TripInfo trip) {
    Date tripStartTime = null;

    // Check each StopTimeUpdate to find the earliest stop arrival time
    StopUpdatesList stopTimeUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData.getUpdates();
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
        String arrival = null;
        if (arrivalTime != null) {
          arrival = arrivalTime.getActual();
        }
        if (arrival == null) { // No Actual time, so use Estimated
          arrival = arrivalTime.getEstimated();
        }
        // If this is the earliest time, use it for the trip start time
        if (arrival != null) {
          Date parsedDate = null;
          try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            parsedDate = df.parse(arrival);
          } catch (Exception e) {
            System.out.println("Exception parsing Estimated time: " + arrival);
          }
          if (tripStartTime == null) {
            tripStartTime = parsedDate;
          } else if (parsedDate.before(tripStartTime)) {
            tripStartTime = parsedDate;
          }
        }
      }
    }
    return tripStartTime;
  }
  
  private List<TripEntry> getLinkTrips() {
    String routeId = _linkRouteId;
    //if (routeId.contains("_")) {
    //  routeId = routeId.substring(routeId.indexOf('_')+1);
    //}
    List<TripEntry> allTrips = _transitGraphDao.getAllTrips();
    List<TripEntry> linkTrips = new ArrayList<TripEntry>();
    for (TripEntry trip : allTrips) {
      if (trip.getRoute().getId().getId().equals(routeId) && trip.getBlock().getId().getId().equals(trip.getId().getId())) {
        linkTrips.add(trip);
      }
    }
    TripComparator tripComparator = new TripComparator();
    Collections.sort(linkTrips, tripComparator);
    
    return linkTrips;
  }
  
  private String getTripForStop(String stopId, String direction, int scheduledTime) {
    int offset = 0;
    for (StopOffset stopOffset : stopOffsets) {
      if (stopOffset.getStopId().equals(stopId)) {
        offset = stopOffset.getOffset();
        break;
      }
    }
    int tripStartTime = scheduledTime - (offset * 60);
    String tripId = "";
    TripEntry lastTrip = null;
    for (TripEntry tripEntry : tripEntries) {
      if (!direction.equals(tripEntry.getDirectionId())) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        }
        continue;
      }
      int startTimeInSecs = getTripStartTimeInSecs(tripEntry);
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
      tripId = tripEntries.get(tripEntries.size()-1).getId().getId();
    }
    
    return tripId;
  }
  /*
  private String getTripDirection(TripInfo trip) {
    String tripDirection = "0";
    StopUpdatesList stopTimeUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData.getUpdates();
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        if (stopTimeUpdate.getStopId().startsWith("NB")) {
          tripDirection = "1";
          break;
        } else if (stopTimeUpdate.getStopId().startsWith("SB")) {
          tripDirection = "0";
          break;
        }
      }
    }
    return tripDirection;
  }
  */
  
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
    private String stopId;
    private String direction;
    private int offset;
    
    public String getStopId() {
      return stopId;
    }
    public void setStopId(String stopId) {
      this.stopId = stopId;
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
    
    public StopOffset(String stopId, String direction, int offset) {
      this.stopId = stopId;
      this.direction = direction;
      this.offset = offset;
    }
  }

}
