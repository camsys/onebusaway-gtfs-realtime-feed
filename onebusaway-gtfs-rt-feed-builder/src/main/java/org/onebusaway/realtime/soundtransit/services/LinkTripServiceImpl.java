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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.transit_data_federation.services.ExtendedCalendarService;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.ServiceIdActivation;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;

public class LinkTripServiceImpl implements LinkTripService {
  private static Logger _log = LoggerFactory.getLogger(LinkTripServiceImpl.class);
  private long timeToUpdateTripIds = 0;
  private static int TRIP_CUTOVER_HOUR = 3; // Trips starting before this hour
                                            // will be set to the previous day
  private TransitGraphDao _transitGraphDao;
  private ExtendedCalendarService _calendarService;
  private LinkStopService _linkStopService;
  private List<TripEntry> tripEntries;
  private static String _linkRouteId;

  public void setTimeToUpdateTripIds(long timeToUpdateTripIds) { // For testing
    this.timeToUpdateTripIds = timeToUpdateTripIds;
  }

  @Autowired
  public void setTransitGraphDao(TransitGraphDao transitGraphDao) {
    _transitGraphDao = transitGraphDao;
  }

  @Autowired
  public void setCalendarService(ExtendedCalendarService calendarService) {
    _calendarService = calendarService;
  }

  @Autowired
  public void setLinkStopServiceImpl(LinkStopService linkStopService) {
    _linkStopService = linkStopService;
  }

  public void setTripEntries(List<TripEntry> tripEntries) {  // For JUnit tests
    this.tripEntries = tripEntries;
  }

  public void setLinkRouteId(String linkRouteId) {
    _linkRouteId = linkRouteId;
  }
  
  @Override
  public void updateTripsAndStops() {
    // Check if date has changed.
    // If it has, update the trips ids so they are valid for this service date
    if ((new Date()).getTime() > timeToUpdateTripIds 
        || tripEntries == null || tripEntries.size() == 0) {
      tripEntries = getLinkTrips();
      Calendar nextUpdate = Calendar.getInstance();  // Start with today
      nextUpdate.set(Calendar.HOUR_OF_DAY, 0);
      nextUpdate.set(Calendar.MINUTE, 0);
      nextUpdate.set(Calendar.SECOND, 0);
      nextUpdate.set(Calendar.MILLISECOND, 0);
      nextUpdate.add(Calendar.DATE, 1);   // Set to tomorrow at 3AM
      nextUpdate.add(Calendar.HOUR_OF_DAY, TRIP_CUTOVER_HOUR);
      timeToUpdateTripIds = nextUpdate.getTimeInMillis();
      SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
      _log.info("List of GTFS trips updated.  Will next be updated after " 
          + sdf.format(new Date(timeToUpdateTripIds)));
      _linkStopService.updateStopOffsets(tripEntries);
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

  @Override
  public String getTripDirection(TripInfo avlTrip) {
    String direction = "";
    if (avlTrip.getDirection() == null) {
      // No direction provided, so check for a stop starting with "NB" or "SB".
      String stopId = (String) avlTrip.getLastStopId();
      if (avlTrip.getStopUpdates() != null) {    // Check the StopIds in Updates
        StopUpdatesList stopTimeUpdateList = avlTrip.getStopUpdates();
        if (stopTimeUpdateList.getUpdates() != null) {
          List<StopUpdate> stopTimeUpdates = stopTimeUpdateList.getUpdates();
          if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
            //for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
            // Check stop updates starting with the last, since direction
            // changes may occur at the beginning.
            for (int i=stopTimeUpdates.size()-1; i >= 0; --i) {
              stopId = stopTimeUpdates.get(i).getStopId();
              if (_linkStopService.isValidLinkStop(stopId)) {
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
      }
    } else if (avlTrip.getDirection().equals("N")) {
      direction = "1";
    } else if (avlTrip.getDirection().equals("S")) {
      direction = "0";
    }
    
    if (direction.isEmpty()) {
      _log.info("No trip direction provided or inferred for trip " + avlTrip.getTripId() + ". Defaulting to outbound.");
      direction = "0";     // Default to south, outbound (southbound)
    }

    return direction;
  }
  
  public TripDescriptor buildTripDescriptor(TripInfo trip) {
    TripDescriptor.Builder td = TripDescriptor.newBuilder();
    String stopId = "";
    int scheduledTime = 0;
    StopUpdatesList stopTimeUpdateData = trip != null ? trip.getStopUpdates() : null;
    List<StopUpdate> stopTimeUpdates = stopTimeUpdateData != null 
        ? stopTimeUpdateData.getUpdates() : null;
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        stopId = stopTimeUpdate.getStopId();
        if (_linkStopService.isValidLinkStop(stopId)) {
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
          // If time is after midnight, add 24 to the hour
          if (hours < TRIP_CUTOVER_HOUR) {
            hours += 24;
          }
          int minutes = Integer.parseInt(timeArray[1]);
          scheduledTime = (hours * 60 + minutes) * 60;
          break;
        }
      }
    }
    // stopId should now be the last stop on the line
    String mappedStopId = null;
    if (stopId != null && !stopId.isEmpty() && trip != null) {
      String direction = getTripDirection(trip);
      mappedStopId = _linkStopService.getGTFSStop(stopId, direction);
    }
    String direction = getTripDirection(trip);
    String tripId = getTripForStop(mappedStopId, direction, scheduledTime);
    if (tripId == null) {
      tripId = "";
    }
    
    // Set trip start time and date from tripStartTime
    Date tripStartTime = getTripStartTime(trip.getStopUpdates());
    if (tripStartTime != null) {
      DateFormat df = new SimpleDateFormat("HH:mm:ss");
      String startTime = df.format(tripStartTime);
      // If time is after midnight, add 24 to the hour and move date back one
      // so the tds can match up the trip to the correct service id.
      Calendar cal = Calendar.getInstance();
      cal.setTime(tripStartTime);
      if (cal.get(Calendar.HOUR_OF_DAY) < TRIP_CUTOVER_HOUR) {
        int hour = cal.get(Calendar.HOUR_OF_DAY) + 24;
        startTime = "" + hour + startTime.substring(2);
        cal.add(Calendar.DAY_OF_YEAR, -1);
        tripStartTime = cal.getTime();
      }
      df = new SimpleDateFormat("yyyyMMdd");
      String startDate = df.format(tripStartTime);
      td.setStartTime(startTime);
      td.setStartDate(startDate);
    }
    td.setTripId(tripId);
    td.setScheduleRelationship(ScheduleRelationship.SCHEDULED);
    td.setRouteId(_linkRouteId);
    
    return td.build();
  }

  private String getTripForStop(String stopId, String direction, int scheduledTime) {
    int offset = 0;
    List<StopOffset> stopOffsets = null;
    stopOffsets = _linkStopService.getStopOffsets(direction);
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
    if (tripId.isEmpty()) {  // Assign it to the last trip
      tripId = tripEntries.get(tripEntries.size()-1).getId().getId();
    }
    return tripId;
  }
  
  private Date getTripStartTime(StopUpdatesList stopTimeUpdatesList) {
    Calendar cal = Calendar.getInstance();
    cal.set(2099, 12, 31);
    Date tripStartTime = null;
    List<StopUpdate> stopTimeUpdates = stopTimeUpdatesList.getUpdates();
    if (stopTimeUpdates != null && stopTimeUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopTimeUpdates) {
        String stopId = stopTimeUpdate.getStopId();
        if (_linkStopService.isValidLinkStop(stopId)) {
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
    }
    return tripStartTime;
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
}
