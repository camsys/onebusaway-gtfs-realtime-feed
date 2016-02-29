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
import java.util.Date;
import java.util.List;

import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtimeConstants;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

public class TUFeedBuilderServiceImpl extends FeedBuilderServiceImpl {
  private static Logger _log = LoggerFactory.getLogger(TUFeedBuilderServiceImpl.class);

  @Override
  public FeedMessage buildFeedMessage(LinkAVLData linkAVLData) {
    // Update the list of trips (done only if the date has changed)
    _linkTripService.updateTripsAndStops();
    
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
    if (trips != null) {
      for (TripInfo trip : trips) {
        StopUpdatesList stopUpdateList = trip.getStopUpdates();
       List<StopUpdate> updateList = stopUpdateList != null ? 
            stopUpdateList.getUpdates() : null;
        if (updateList == null || updateList.size() < 2) {
          continue;
        }
        TripUpdate.Builder tu = TripUpdate.newBuilder();
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
        TripDescriptor td = _linkTripService.buildTripDescriptor(trip);
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
            _log.error("Exception parsing LastUpdatedDate time: " + lastUpdatedDate);
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
    } else {
      _log.info("buildTUMessage(): no trip data found");
    }
    tripUpdatesFM = feedMessageBuilder.build();
    _log.info("trip updates: " + tripUpdateEntityCount);
    
  return tripUpdatesFM;
  }

  private List<StopTimeUpdate> buildStopTimeUpdateList(TripInfo trip) {
    List<StopTimeUpdate> stopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    StopUpdatesList stopUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopUpdates = stopUpdateData.getUpdates();
    if (stopUpdates != null && stopUpdates.size() > 0) {
      List<StopUpdate> modStopUpdates = new ArrayList<>();
      for (int i=0; i<stopUpdates.size(); ++i) {
        String stopId = stopUpdates.get(i).getStopId();
        if (_linkStopService.isValidLinkStop(stopId)) {
          modStopUpdates.add(stopUpdates.get(i));
        }
      }
      stopUpdates = modStopUpdates;
      String tripDirection = _linkTripService.getTripDirection(trip);
      List<StopTimeUpdate> dummyStopTimeUpdates =
          buildPseudoStopTimeUpdates(stopUpdates, tripDirection);
      stopTimeUpdateList.addAll(dummyStopTimeUpdates);
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
                    arrivalTime, tripDirection, "");
            stopTimeUpdateList.add(stopTimeUpdate);
          }
        }
      }
    }
    return stopTimeUpdateList;
  }

  private List<StopTimeUpdate> buildPseudoStopTimeUpdates(List<StopUpdate> stopUpdates,
      String direction) {
    List<StopTimeUpdate> dummyStopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    List<StopOffset> stopOffsets = _linkStopService.getStopOffsets(direction);
    
    // Make sure the first real stop is headed in the right direction,
    // since a train may come onto the line at a NB platform, for instance,
    // and immediately switch to the SB platform and continue SB for the
    // rest of the trip.
    String wrongDir = "SB";
    if (direction.equals("0")) {
      wrongDir = "NB";
    }
    String firstRealStopId = stopUpdates.get(0).getStopId();
    int firstRealStopIdx = 0;
    if (firstRealStopId.startsWith(wrongDir)) {
      for (int i=0; i < stopUpdates.size(); i++) {
        if (!stopUpdates.get(i).getStopId().startsWith(wrongDir)) {
          firstRealStopId = stopUpdates.get(i).getStopId();
          firstRealStopIdx = i;
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
          getAdjustedTime(firstRealStopId, arrivalTimeDetails, pseudoStopId, direction);
      if (!dummyArrivalTime.isEmpty()) {
        StopTimeUpdate stopTimeUpdate = 
            buildStopTimeUpdate(pseudoStopId, dummyArrivalTime, direction, "SKIPPED");
      
        dummyStopTimeUpdateList.add(stopTimeUpdate);
      }
      pseudoStopId = stopOffsets.get(++idx).getLinkStopId();
    }
    return dummyStopTimeUpdateList;
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
        stopId = _linkStopService.getGTFSStop(stopId, direction);
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

  /*
   * This method is used when creating pseudo entries for a trip covering only
   * part of the route to prevent OBA from creating its own entries for 
   * those stops.
   */
  private String getAdjustedTime(String baseStopId, 
    ArrivalTime arrivalTimeDetails, String targetStopId, String direction) {
    List<StopOffset> stopOffsets = _linkStopService.getStopOffsets(direction);
    String adjustedTime = "";
    if (arrivalTimeDetails != null) {
      int numberOfStops = stopOffsets.size();
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

}
