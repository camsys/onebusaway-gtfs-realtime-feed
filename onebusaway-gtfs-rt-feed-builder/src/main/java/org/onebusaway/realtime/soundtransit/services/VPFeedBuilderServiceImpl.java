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
import java.util.Date;
import java.util.List;

import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtimeConstants;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import org.springframework.beans.factory.annotation.Autowired;

public class VPFeedBuilderServiceImpl extends FeedBuilderServiceImpl {
  private static Logger _log = LoggerFactory.getLogger(VPFeedBuilderServiceImpl.class);
  private AvlParseService _avlParseService;

  @Autowired
  public void setAvlParseService(AvlParseService service) {
    _avlParseService = service;
  }

  private FeedMessage buildFeedMessage(LinkAVLData linkAVLData) {
    // Update the list of trips (done only if the date has changed
    _linkTripService.updateTripsAndStops();
    
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
      for (TripInfo trip : trips) {
       // If there are no stop time updates, don't generate a VehiclePosition
        // for this trip.
        StopUpdatesList stopUpdateList = trip.getStopUpdates();
        List<StopUpdate> updateList = stopUpdateList != null ? 
            stopUpdateList.getUpdates() : null;
        if (updateList == null || updateList.size() < 2) {
          continue;
        }
        if (!_avlParseService.hasPredictions(trip)) {
          // ignore historical records
          continue;
        }
        VehiclePosition.Builder vp = VehiclePosition.newBuilder();
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        Position.Builder positionBuilder = Position.newBuilder();
        String vehicleId = _avlParseService.hashVehicleId(trip.getVehicleId());
        if (vehicleId == null) {
          _log.error("encounterd null vehicleId for trip=" + trip + ", discarding");
          continue;
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
          timestamp = _avlParseService.parseAvlTimeAsSeconds(lastUpdatedDate);
        }
        timestamp = timestamp != 0L ? timestamp : System.currentTimeMillis()/1000;
        vp.setTimestamp(timestamp);

        StopUpdate nextStop = _linkStopService.findNextStopOnTrip(trip.getStopUpdates());
        if (nextStop == null) {
          _log.error("Cannot determine next stop id for trip " + trip.getTripId());
          continue;
        }
        String stopId = nextStop.getStopId();
        if (stopId == null) {
          stopId = "";
        } else {
          String direction = _linkTripService.getTripDirection(trip);
          stopId = _linkStopService.getGTFSStop(stopId, direction);
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
        TripDescriptor td = _linkTripService.buildFrequencyTripDescriptor(trip);
        vp.setTrip(td);

        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(vehicleId);
        entity.setVehicle(vp);
        feedMessageBuilder.addEntity(entity);
        ++vehiclePositionEntityCount;
      }
    }
      
    vehiclePositionsFM = feedMessageBuilder.build();
    _log.info("vehicle position updates: " + vehiclePositionEntityCount);
    return vehiclePositionsFM;
    
  }

  @Override
  public FeedMessage buildFrequencyFeedMessage(LinkAVLData linkAVLData) {
    return buildFeedMessage(linkAVLData);
  }

  @Override
  public FeedMessage buildScheduleFeedMessage(LinkAVLData linkAVLData) {
    return buildFeedMessage(linkAVLData);
  }

}
