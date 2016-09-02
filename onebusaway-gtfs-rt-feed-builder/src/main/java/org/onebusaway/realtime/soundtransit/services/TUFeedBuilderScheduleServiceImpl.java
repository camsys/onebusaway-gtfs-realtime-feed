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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

public class TUFeedBuilderScheduleServiceImpl {

  private static Logger _log = LoggerFactory.getLogger(TUFeedBuilderScheduleServiceImpl.class);
  // TODO make this guava to records expire
  private Map<String, CacheRecord> positionCache = new HashMap<String, CacheRecord>();
  private TUFeedBuilderComponent _component;
  protected LinkTripService _linkTripService;
  protected LinkStopService _linkStopService;
  private AvlParseService avlParseService = new AvlParseServiceImpl();

  @Autowired
  public void setLinkTripServiceImpl(LinkTripService linkTripService) {
    _linkTripService = linkTripService;
  }

  @Autowired
  public void setLinkStopServiceImpl(LinkStopService linkStopService) {
    _linkStopService = linkStopService;
  }
  
  @Autowired
  public void setTUFeedBuilderComponent(TUFeedBuilderComponent component) {
    _component = component;
  }
  
  public FeedMessage buildScheduleFeedMessage(LinkAVLData linkAVLData) {
    FeedMessage.Builder feedMessageBuilder = _component.buildHeader();
    
    
    TripInfoList tripInfoList = linkAVLData.getTrips();
    List<TripInfo> trips = tripInfoList != null ? tripInfoList.getTrips() : null;
    if (trips != null) {
      for (TripInfo trip : trips) {
        
        String vehicleId = avlParseService.hashVehicleId(trip.getVehicleId());
        long lastUpdatedInSeconds = getLastUpdatedTimestampForTrip(vehicleId, trip);
        ServiceDate serviceDate = estimateServiceDate(new Date(lastUpdatedInSeconds*1000));
        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId(trip.getVehicleId());
        TripUpdate.Builder tu = TripUpdate.newBuilder();
        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        /*
         * AVL TripId is not like GTFS Trip Id, it is of format BlockSeq: InternalTripNumber
         * and remains consistent across the block
         */
        vd.setId(trip.getTripId()); 
        tu.setVehicle(vd.build());
        TripDescriptor td = _linkTripService.buildScheduleTripDescriptor(trip, 
            serviceDate, lastUpdatedInSeconds);
        if (td == null) {
          _log.error("unmatched trip for trip " + trip.getTripId());
          continue;
        }
        tu.setTrip(td);
        _log.debug("building trip " + td.getTripId() + "(" + trip.getTripId() + ")");
        tu.addAllStopTimeUpdate(buildScheduleStopTimeUpdateList(trip, td.getTripId(), lastUpdatedInSeconds));
        tu.setTimestamp(lastUpdatedInSeconds);
        // use effective schedule deviation so OBA plots position accurately
        Integer delay = _linkTripService.calculateEffectiveScheduleDeviation(trip, td.getTripId(), serviceDate, lastUpdatedInSeconds);
        
        if (delay != null) {
          _log.info(" delay= " + delay + " for vehicle=" + trip.getVehicleId());
          tu.setDelay(delay);
        }
        entity.setTripUpdate(tu.build());
        feedMessageBuilder.addEntity(entity.build());
      } // end for trips
    } // end if trips != null
    return feedMessageBuilder.build();

  }

  // last modified updates on each request, we want it updated only when the data changed
  // use the vehicle position as an indicator that something has changed
  private long getLastUpdatedTimestampForTrip(String vehicleId, TripInfo trip) {
    Long lastUpdated = avlParseService.parseAvlTimeAsSeconds(trip.getLastUpdatedDate());
    trip.getLat();
    trip.getLon();
    CacheRecord cr = new CacheRecord(trip.getLat(), trip.getLon(), lastUpdated);
    CacheRecord lastCache = positionCache.get(vehicleId);
    if (lastCache == null) {
      positionCache.put(vehicleId, cr);
      return cr.lastUpdated;
    }
    // if the positions equal, return the older timestamp
    if (lastCache.equals(cr)) {
      _log.info("no update for " + vehicleId);
      return lastCache.lastUpdated;
    }
    return cr.lastUpdated;
  }


  /**
   * LINK runs a 25 hour service day.  Updates between 00:00 and 03:00 are on
   * the previous service day.
   * Looking at the GTFS, Last trip is ~25, first trip is ~04
   */
  private ServiceDate estimateServiceDate(Date date) {
    // if between 00:00 and 3:00 we are likely the previous day
    Calendar c = Calendar.getInstance();
    c.setTime(date);
    if (c.get(Calendar.HOUR_OF_DAY) <= 3) {
      c.add(Calendar.DAY_OF_YEAR, -1);
      return new ServiceDate(c.getTime());
    }
    // otherwise default the service date to today
    ServiceDate sd = new ServiceDate(date);
    return sd;
  }

  private Iterable<? extends StopTimeUpdate> buildScheduleStopTimeUpdateList(
      TripInfo trip, String tripId, long lastUpdatedInSeconds) {
    List<StopTimeUpdate> stopTimeUpdateList = new ArrayList<StopTimeUpdate>();
    StopUpdatesList stopUpdateData = trip.getStopUpdates();
    List<StopUpdate> stopUpdates = stopUpdateData.getUpdates();
    // filter on valid stops (drop tiplocs)
    if (stopUpdates != null && stopUpdates.size() > 0) {
      List<StopUpdate> filteredStopUpdates = new ArrayList<>();
      for (int i=0; i<stopUpdates.size(); ++i) {
        String stopId = stopUpdates.get(i).getStopId();
        if (_linkStopService.isValidLinkStop(stopId)) {
          filteredStopUpdates.add(stopUpdates.get(i));
        }
      }
      // we know tripId, lookup direction from bundle
      
      List<StopTimeUpdate> stopTimeUpdates = findArrivalTimeUpdates(filteredStopUpdates, tripId, lastUpdatedInSeconds);
      if (stopTimeUpdates != null)
        stopTimeUpdateList.addAll(stopTimeUpdates);
    }
    return stopTimeUpdateList;

  }

  /*
   * return all stop time updates in the future.  In the future is
   * any timestamp greater than lastUpdated.
   */
  private List<StopTimeUpdate> findArrivalTimeUpdates(
      List<StopUpdate> stopUpdates, String tripId, long lastUpdatedInSeconds) {
      List<StopTimeUpdate> updates = new ArrayList<StopTimeUpdate>();
      for (int i = 0; i < stopUpdates.size(); i++) {
        StopUpdate stopUpdate = stopUpdates.get(i);
        String tripDirection = _linkTripService.getTripDirectionFromTripId(tripId);
        ArrivalTime arrival = stopUpdate.getArrivalTime();
        // buildStopTimeUpdate will ensure prediction is in future
        StopTimeUpdate stu = _component.buildStopTimeUpdate(stopUpdate.getStopId(),
            arrival.getEstimated(), tripDirection, "", lastUpdatedInSeconds);
        if (stu != null) {
          updates.add(stu);
        }
      }
    return updates;
  }

  private static class CacheRecord {
    String lat = null;
    String lon = null;
    Long lastUpdated = null;
    public CacheRecord(String lat, String lon, Long lastUpdated) {
      this.lat = lat;
      this.lon = lon;
      this.lastUpdated = lastUpdated;
    }
    public boolean equals(Object obj) {
      if (obj == null) return false;
      if (!(obj instanceof CacheRecord)) return false;
      CacheRecord cr = (CacheRecord)obj;
      // we don't consider the timestamp
      return lat.equals(cr.lat)
          && lon.equals(cr.lon);
    }
  }
  

}
