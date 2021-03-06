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

import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripComparator;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockTripEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.ServiceIdActivation;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.blockConfiguration;

public class LinkTripServiceImpl implements LinkTripService {
  private static final int MAX_RECURSE = 80;
  private static Logger _log = LoggerFactory.getLogger(LinkTripServiceImpl.class);
  private long timeToUpdateTripIds = 0;
  private static int TRIP_CUTOVER_HOUR = 3; // Trips starting before this hour
                                            // will be set to the previous day

  private TransitDataServiceFacade _tdsf;

  private LinkStopService _linkStopService;
  private List<TripEntry> tripEntries;
  private AvlParseService avlParseService = null;
  TripComparator tripComparator = new TripComparator();
  private String _defaultAgencyId = "40";
  private String _agencyId;
  private Integer _linkRouteKey = null;
  private static Integer DEFAULT_LINK_ROUTE_KEY = 599;
  private boolean _overrideScheduleTime = true;
  private boolean _interpolateUnscheduledTrips = true;

  @Autowired
  public void setTransitDataServiceFacade(TransitDataServiceFacade tds) {
    _tdsf = tds;
  }

  @Autowired
  public void setAvlParseService(AvlParseService service) {
    avlParseService = service;
  }

  private Integer getLinkRouteKey() {
    if (_linkRouteKey == null) {
      return DEFAULT_LINK_ROUTE_KEY;
    }
    return _linkRouteKey;
  }

  public void setLinkRouteKey(Integer linkRouteKey) {
    _linkRouteKey = linkRouteKey;
  }

  public void setAgencyId(String agencyId) {
    _agencyId = agencyId;
  }
  
  public String getAgencyId() {
    if (_agencyId == null)
      return _defaultAgencyId;
    return _agencyId;
  }
  
  public void setTimeToUpdateTripIds(long timeToUpdateTripIds) { // For testing
    this.timeToUpdateTripIds = timeToUpdateTripIds;
  }



  @Autowired
  public void setLinkStopServiceImpl(LinkStopService linkStopService) {
    _linkStopService = linkStopService;
  }

  public void setTripEntries(List<TripEntry> tripEntries) {  // For JUnit tests
    this.tripEntries = tripEntries;
  }

  public void setOverrideScheduleTime(boolean override) {
    _overrideScheduleTime = override;
  }

  public void setInterpolateUnscheduledTrips(boolean interpolate) {
    _interpolateUnscheduledTrips = interpolate;
  }

  public AvlParseService getAvlParseService() {
    return avlParseService;
  }

  public String getLinkRouteId() {
    return avlParseService.getLinkRouteId();
  }

  @Override
  public void updateTripsAndStops() {
    // Check if date has changed.
    // If it has, update the trips ids so they are valid for this service date
    if ((new Date()).getTime() > timeToUpdateTripIds
        || tripEntries == null || tripEntries.size() == 0) {
      tripEntries = getLinkTrips();

      Calendar nextUpdate = getNextUpdate();
      timeToUpdateTripIds = nextUpdate.getTimeInMillis();
      SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
      _log.info("List of GTFS trips updated.  Will next be updated after " 
          + sdf.format(new Date(timeToUpdateTripIds)));
      _linkStopService.updateStopOffsets();
    }
  }

  private Calendar getNextUpdate() {
    Calendar nextUpdate = Calendar.getInstance();  // Start with today
    nextUpdate.set(Calendar.HOUR_OF_DAY, 0);
    nextUpdate.set(Calendar.MINUTE, 0);
    nextUpdate.set(Calendar.SECOND, 0);
    nextUpdate.set(Calendar.MILLISECOND, 0);
    nextUpdate.add(Calendar.DATE, 1);   // Set to tomorrow at 3AM
    nextUpdate.add(Calendar.HOUR_OF_DAY, TRIP_CUTOVER_HOUR);
    return nextUpdate;
  }

  private List<TripEntry> getLinkTrips() {
    return _linkStopService.getStopOffsets().getLinkTrips();
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
  
  public TripDescriptor buildFrequencyTripDescriptor(TripInfo trip) {
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
    // trip for stop needs to consider service date
    String tripId = getTripForStop(mappedStopId, direction, scheduledTime, avlParseService.parseAvlTimeAsMillis(trip.getLastUpdatedDate()));
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
    td.setRouteId(getLinkRouteId());
    
    return td.build();
  }

  public TripDescriptor buildScheduleTripDescriptor(TripInfo trip, ServiceDate serviceDate, long lastUpdatedInSeconds) {
    if (trip == null) return null;
    TripDescriptor.Builder td = TripDescriptor.newBuilder();
    String tripId = lookupTripByRunId(getBlockRun(trip), 
        findBestStopTimeUpdateScheduledTime(trip.getStopUpdates(), lastUpdatedInSeconds), 
        trip.getDirection(),
        serviceDate);
    
    // unmatched trip, nothing we can do
    if (tripId == null) {
      _log.debug("unmatched trip for avl trip " + trip.getTripId());
      return null;
    }

    td.setTripId(tripId);
    return td.build();
  }

  private String getBlockRun(TripInfo trip) {
    _log.debug("tripId=" + trip.getTripId());
    return parseRun(trip.getTripId());
  }

  private Long findBestStopTimeUpdateScheduledTime(StopUpdatesList stopUpdates, long lastUpdatedInSeconds) {
    StopUpdate timeUpdate = findNextStopTimeUpdate(stopUpdates, lastUpdatedInSeconds);
    if (timeUpdate == null) return null;
    Long scheduledTime = avlParseService.parseAvlTimeAsMillis(timeUpdate.getArrivalTime().getScheduled());
    if (scheduledTime == 0) {
      if (_interpolateUnscheduledTrips) {
        // we are unscheduled/off schedule, use lastUpdated so we can map to active trip/block
        _log.info("found illegal schedule time=" + timeUpdate.getArrivalTime().getScheduled() + " for update="
                + timeUpdate + ", guessing at now");
        return lastUpdatedInSeconds * 1000;
      } else {
        // when configured, discard unscheduled trips as we may guess wrong!
        _log.info("found illegal schedule time=" + timeUpdate.getArrivalTime().getScheduled() + " for update="
                + timeUpdate + ", discarding");
        return null;
      }
    }
    
    if (_overrideScheduleTime && Math.abs(scheduledTime - System.currentTimeMillis()) > 2 * 60 *60 *1000) {
     // we have a crazy schedule, assume now instead
      _log.error("ignoring scheduleTime of " + timeUpdate.getArrivalTime().getScheduled());
      return System.currentTimeMillis();
    }
    
    return scheduledTime;
  }
  
  /**
   * next is defined as the first prediction in the future as compared to lastUpdatedInSeconds.
   * If no upates are in the future, the last update is returned to signify the end of the trip.
   */
  private StopUpdate findNextStopTimeUpdate(StopUpdatesList stopUpdates, long lastUpdatedInSeconds) {
    if (stopUpdates == null || stopUpdates.getUpdates() == null || stopUpdates.getUpdates().isEmpty()) {
      _log.error("no stopUpdates, nothing to do");
      return null;
    }
    for (StopUpdate stopUpdate : stopUpdates.getUpdates()) {
      if (stopUpdate.getArrivalTime() != null) {
        if (stopUpdate.getArrivalTime().getEstimated() != null) {
          Long predictedTime = avlParseService.parseAvlTimeAsSeconds(stopUpdate.getArrivalTime().getEstimated());
          if (predictedTime >= lastUpdatedInSeconds) {
            return stopUpdate;
          }
        }
      }
    }
    // if we made it here the trip is complete, retrieve the last stop update
    int last = stopUpdates.getUpdates().size();
    if (last > 0)
      return stopUpdates.getUpdates().get(last - 1);

    _log.error("could not find valid arrival time");
    return null;
  }
  
  ScheduledBlockLocation lookupBlockLocation(String blockRunNumber, Long scheduleTime, String avlDirection, ServiceDate serviceDate) {
    _log.debug("lookupTrip(" + blockRunNumber + ", " + scheduleTime + ")");
    List<AgencyAndId> blockIds = lookupBlockIds(blockRunNumber);
    if (blockIds == null) {
      _log.debug("no suitable blockIds for run=" + blockRunNumber);
      return null;
    }
    BlockInstance instance = null;
    ScheduledBlockLocation best = null;
    Long bestMetric = null;
    
    for (AgencyAndId blockId : blockIds) {
      _log.debug("found blockId=" + blockId);
      if (scheduleTime == null) {
        _log.error("missing scheduleTime for blockRunNumber=" + blockRunNumber);
        continue;
      }
      try {
        instance = _tdsf.getBlockInstance(blockId, serviceDate.getAsDate().getTime());
      } catch (Throwable t) {
        _log.error("getBlockInstance failed", t);
        _log.error("invalid blockId=" + blockId + " for blockRunNumber=" 
            + blockRunNumber + ", scheduleTime=" + new Date(scheduleTime)
            + ", serviceDate=" + serviceDate);
        continue;
      }
      if (instance == null) {
        _log.debug("unmatched block=" + blockId + " for time= " + scheduleTime
            + "(" + new Date(scheduleTime) + ")" + " and run=" + blockRunNumber);
        continue;
      }
      
      int secondsIntoDay = (int) TimeUnit.SECONDS.convert(scheduleTime - serviceDate.getAsDate().getTime(), TimeUnit.MILLISECONDS);
      
      // we've found a block, now we need to search the block for the appropriate active trip that matches the avlDirection
      
      ScheduledBlockLocation blockLocation = findBestBlockLocation(instance, avlDirection, secondsIntoDay, 0);
      if (blockLocation == null || blockLocation.getActiveTrip() == null && blockLocation.getActiveTrip().getTrip() == null)
        continue;
      
      // verify the active trip makes sense and log if not
      List<StopTimeEntry> stopTimes = blockLocation.getActiveTrip().getTrip().getStopTimes();
      long firstStopTime = stopTimes.get(0).getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
      long lastStopTime = stopTimes.get(stopTimes.size()-1).getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
      long window = 20 * 60 * 1000; // 20 minutes
      
      // to be fair, we expand our search to the next trip in the block
      BlockTripEntry nextTrip = blockLocation.getActiveTrip().getNextTrip();
      if (nextTrip != null) {
        List<StopTimeEntry> nextTripStopTimes = nextTrip.getTrip().getStopTimes();
        lastStopTime = nextTripStopTimes.get(nextTripStopTimes.size()-1).getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
      }
      if (scheduleTime > lastStopTime + window || firstStopTime - window > scheduleTime) {
        _log.error("blockLocation outside of trip: blockRunNumber=" + blockRunNumber
            + ", scheduleTime=" + new Date(scheduleTime) + " (" + scheduleTime + ")"
            + ", serviceDate=" + serviceDate
            + ", blockId=" + blockId
            + ", tripId=" + blockLocation.getActiveTrip().getTrip().getId()
            + ", firstStopTime=" + new Date(firstStopTime)
            + ", lastStopTime=" + new Date(lastStopTime));
      }

      Long metric = rateBlockLocation(blockLocation, scheduleTime, avlDirection, serviceDate);
      if (bestMetric == null || metric < bestMetric) {
        if (bestMetric != null) {
          _log.info("metric=" + metric + " is better than " + bestMetric + " for train " + blockRunNumber);
        }
        bestMetric = metric;
        best = blockLocation;
      }
    }

    if (best == null) {
      // the block is no longer active (old data?)
      _log.info("fall through for " + blockRunNumber + ", " + scheduleTime + ", " + serviceDate
              + " with considered blockIds=" + blockIds);
    }
    return best;
    
  }

  /**
   * here we find the (absolute) seconds between scheduleTime and the closest stop on the active trip.
   * @param blockLocation
   * @param scheduleTime
   * @param avlDirection
   * @param serviceDate
   * @return
   */
  private Long rateBlockLocation(ScheduledBlockLocation blockLocation,
                                 Long scheduleTime,
                                 String avlDirection,
                                 ServiceDate serviceDate) {

    Long closestStopDelta = null;
    List<StopTimeEntry> stopTimes = blockLocation.getActiveTrip().getTrip().getStopTimes();
    for (StopTimeEntry stopTime : stopTimes) {
      long delta = stopTime.getArrivalTime() * 1000 + serviceDate.getAsDate().getTime() - scheduleTime;
      if (closestStopDelta == null || delta < closestStopDelta) {
        closestStopDelta = delta;
      }
    }
    return Math.abs(closestStopDelta);
  }

  ScheduledBlockLocation findBestBlockLocation(BlockInstance instance, String avlDirection, int secondsIntoDay, int recursionCount) {
    ScheduledBlockLocation blockLocation = _tdsf.getScheduledBlockLocationFromScheduledTime(instance.getBlock(), secondsIntoDay);
    if (blockLocation == null) return null;
    if (avlDirection != null) {
      _log.debug("bi " + instance.getBlock().getTrips() + " found trip= " + blockLocation.getActiveTrip().getTrip()
          + " and direction=" + blockLocation.getActiveTrip().getTrip().getDirectionId());
      String locDirection = blockLocation.getActiveTrip().getTrip().getDirectionId();
      if (!matchesDirection(avlDirection, locDirection)) {
        // log potential schedule mismatches (GTFS out of sync with feed)
        _log.debug("trip " + blockLocation.getActiveTrip().getTrip().getId() + " direction of " + locDirection
            + " contradicts feed direction of " + avlDirection);
        // recurse going back in time 5 minutes
        if (recursionCount > MAX_RECURSE) {
          _log.error("infinite recursion (" + MAX_RECURSE + ") detected for trip " + blockLocation.getActiveTrip().getTrip());
          return null;
        }
        recursionCount++;
        return findBestBlockLocation(instance, avlDirection, secondsIntoDay - (5 * 60), recursionCount);
      }
    }
    if (recursionCount > 0)
      _log.info("best block recursed " + recursionCount + " time(s)");
    return blockLocation;
    
  }
  
  // given a blockId find the active trip for the schedule time
  String lookupTripByRunId(String blockRunNumber, Long scheduleTime, String avlDirection, ServiceDate serviceDate) {
    ScheduledBlockLocation blockLocation = lookupBlockLocation(blockRunNumber, scheduleTime, avlDirection, serviceDate);
    if (blockLocation == null || blockLocation.getActiveTrip() == null && blockLocation.getActiveTrip().getTrip() == null) {
      _log.error("tossing blockRunNumber " + blockRunNumber);
      return null;
    }
    
    return blockLocation.getActiveTrip().getTrip().getId().getId();
  }


  // given a block run lookup the possible block ids served by that run
  List<AgencyAndId> lookupBlockIds(String blockRunNumber) {
    List<AgencyAndId> agencyBlockIds = new ArrayList<AgencyAndId>();
    
    try {
      List<Integer> blockIds = _tdsf.getBlockIds(getLinkRouteKey(), Integer.parseInt(blockRunNumber));
      if (blockIds == null) {
        _log.debug("missing blockId for " + getLinkRouteKey() + " / " + blockRunNumber);
        return null;
      }
      for (Integer blockId : blockIds) {
        if (blockId != null) {
          agencyBlockIds.add(new AgencyAndId(getAgencyId(), blockId.toString()));
        }
      }
    } catch (NumberFormatException nfe) {
      _log.error("nfe for input=|" + blockRunNumber + "|");
      return null;
    }
    return agencyBlockIds;
  }

  private String parseRun(String tripId) {
    if (tripId.contains(":")) 
      return tripId.substring(0, tripId.indexOf(":"));
    return null;
  }

  private String getTripForStop(String stopId, String direction, int scheduledTime, long serviceDateInMillis) {
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

      boolean isActiveToday = false;
      try {
        isActiveToday = _tdsf.areServiceIdsActiveOnServiceDate(gtfsTripEntry.getServiceId(), new Date(serviceDateInMillis));
      } catch (Throwable t) {
        // calendar search is sensitive to bad data
        _log.error("exception checking service id=" + gtfsTripEntry.getServiceId() + " with serviceDate= " + new Date(serviceDateInMillis), t);
      }
      if (!isActiveToday) {
        _log.debug("discarding trip " + gtfsTripEntry.getId() + " as it is not active on serviceDate " + new Date(serviceDateInMillis));
        continue;
      }
      //int startTimeInSecs = getTripStartTimeInSecs(tripEntry);
      if (tripStartTime < tripComparator.getTripStartTimeInSecs(gtfsTripEntry)) {
        if (lastTrip != null) {
          tripId = lastTrip.getId().getId();
        } else {  // Earlier than the first entry, so just use the first stop.
          tripId = gtfsTripEntry.getId().getId();
        }
        break;
      }
      lastTrip = gtfsTripEntry;
    }
    if (tripId.isEmpty() && tripEntries.size() > 1) {  // Assign it to the last trip
      tripId = tripEntries.get(tripEntries.size()-1).getId().getId();
    } else if (tripId.isEmpty() && tripEntries.size() == 1) {
      tripId = tripEntries.get(0).getId().getId();
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
  

  @Override
  public String getTripDirectionFromTripId(String tripId) {
    TripEntry trip = _tdsf.getTripEntryForId(new AgencyAndId(getAgencyId(), tripId));
    if (trip == null) return null;
    return trip.getDirectionId();
  }

  @Override
  public Integer calculateDelay(TripInfo trip, String tripId, ServiceDate serviceDate, long lastUpdatedInSeconds) {
    
    StopUpdate stopTimeUpdate = findNextStopTimeUpdate(trip.getStopUpdates(), lastUpdatedInSeconds);
    if (stopTimeUpdate == null) {
      _log.info("no updates for trip " + trip.getTripId());
      return null;
    }
    
    Long scheduledTime = avlParseService.parseAvlTimeAsMillis(stopTimeUpdate.getArrivalTime().getScheduled());
    String gtfsStopId = _linkStopService.getGTFSStop(stopTimeUpdate.getStopId(), getTripDirection(trip));
    TripEntry tripEntry = _tdsf.getTripEntryForId(new AgencyAndId(getAgencyId(), tripId));

    // unit tests don't have a populated transit graph so fall back on scheduled time from feed
    if (tripEntry != null) {
      for (StopTimeEntry s : tripEntry.getStopTimes()) {
        if (s.getStop().getId().getId().equals(gtfsStopId)) {
          Long exactScheduledTime = s.getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
          _log.info("updating schedule arrival (" + new Date(scheduledTime) 
              + ") to (" + new Date(exactScheduledTime) + ") for stop=" + gtfsStopId 
              + " compared to " + s.getStop());
          scheduledTime = exactScheduledTime;
        }
      }
    }
    
    
    Integer diffInSeconds = (int) ((avlParseService.parseAvlTimeAsMillis(stopTimeUpdate.getArrivalTime().getEstimated()) 
        - scheduledTime) / 1000); 
    _log.debug( stopTimeUpdate.getArrivalTime().getEstimated() 
       +  " - " 
       + stopTimeUpdate.getArrivalTime().getScheduled()
       + " = "
       + diffInSeconds);
    
    _log.info("delay = " + diffInSeconds + " for stop " + stopTimeUpdate.getStopId()
      + " (" + stopTimeUpdate.getStationName() + ")"
      + " on tripId=" + trip.getVehicleId() + " with scheduled Time of "
      + stopTimeUpdate.getArrivalTime().getScheduled());
    
    return diffInSeconds;
  }
  
  @Override
  public Integer calculateEffectiveScheduleDeviation(TripInfo trip, String tripId, ServiceDate serviceDate, long lastUpdatedInSeconds) {
    try {
      TripEntry tripEntry = _tdsf.getTripEntryForId(new AgencyAndId(getAgencyId(), tripId));
      
      // Unit tests: no transit graph. Fall back to delay.
      if (tripEntry == null || trip.getLat() == null || trip.getLon() == null) {
        return calculateDelay(trip, tripId, serviceDate, lastUpdatedInSeconds);
      }
      
      long time = avlParseService.parseAvlTimeAsSeconds(trip.getLastUpdatedDate());
      double lat;
      double lon;
      try {
        lat = Double.parseDouble(trip.getLat());
        lon = Double.parseDouble(trip.getLon());
      } catch (Exception any) {
        _log.error("issue parsing lat/lon, falling back to simple delay for tripId " + tripId);
        return calculateDelay(trip, tripId, serviceDate, lastUpdatedInSeconds);
      }
      long serviceDateTime = serviceDate.getAsDate().getTime();
      
      Long effSchedTimeSec = getEffectiveScheduleTime(trip, tripEntry, lat, lon, time, serviceDateTime);
      if (effSchedTimeSec == null) return null;
      long effSchedTime = effSchedTimeSec + (serviceDateTime/1000);
      _log.debug("deviation= (updateTime=" + new Date(time*1000) + ") - (effectiveTime="
              + new Date(effSchedTime*1000) + ") = " + (time - effSchedTime) +"s");
      return (int) (time - effSchedTime);
    } catch (Exception any) {
      _log.error("exception processing effective schedule for trip " + trip, any);
      return null;
    }
  }
  
  private Long getEffectiveScheduleTime(TripInfo tripInfo, TripEntry trip, double lat, double lon, long timestamp, long serviceDate) {
    
    ServiceIdActivation serviceIds = new ServiceIdActivation(trip.getServiceId());
    BlockConfigurationEntry blockConfig = blockConfiguration(trip.getBlock(), serviceIds, trip);
    BlockInstance block = new BlockInstance(blockConfig, serviceDate);
    CoordinatePoint location = new CoordinatePoint(lat, lon);
    _log.debug("bestLocation(0," + trip.getTotalTripDistance() + ")");
    ScheduledBlockLocation loc = _tdsf.getBestScheduledBlockLocationForLocation(
        block, location, timestamp, 0, trip.getTotalTripDistance());
    if (loc != null && loc.getActiveTrip() != null) {
      _log.debug("return scheduleTime of " + loc.getScheduledTime()
              + " (" + new Date( serviceDate + loc.getScheduledTime() * 1000) + ")"
              + " for trip=" + trip.getId()
              + " lat=" + lat + ", lon=" + lon + ", scheduleTime=" + timestamp
              + ", totalTripDistance=" + trip.getTotalTripDistance());
      return (long) loc.getScheduledTime();
    }
    return null;
  }

  private boolean matchesDirection(String feedDirection, String gtfsDirection) {
    if ("S".equals(feedDirection) && "0".equals(gtfsDirection))
      return true;
    if ("N".equals(feedDirection) && "1".equals(gtfsDirection))
      return true;
    return false;
  }
  

}
