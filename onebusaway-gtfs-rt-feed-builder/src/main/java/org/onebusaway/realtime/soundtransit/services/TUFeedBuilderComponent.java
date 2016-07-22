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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtimeConstants;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;

public class TUFeedBuilderComponent {

  private static Logger _log = LoggerFactory.getLogger(TUFeedBuilderComponent.class);
  
  private AvlParseService avlParseService = new AvlParseServiceImpl();
  protected LinkTripService _linkTripService;
  protected LinkStopService _linkStopService;

  @Autowired
  public void setLinkTripServiceImpl(LinkTripService linkTripService) {
    _linkTripService = linkTripService;
  }

  @Autowired
  public void setLinkStopServiceImpl(LinkStopService linkStopService) {
    _linkStopService = linkStopService;
  }

  public FeedMessage.Builder buildHeader() {
    FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
    FeedHeader.Builder header = FeedHeader.newBuilder();
    header.setTimestamp(System.currentTimeMillis()/1000);
    header.setIncrementality(Incrementality.FULL_DATASET);
    header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
    feedMessageBuilder.setHeader(header);

    return feedMessageBuilder;

  }

  public StopTimeUpdate buildStopTimeUpdate(String stopId, String arrivalTime,
      String direction, String scheduleRelationship,
      Long lastUpdatedInSeconds) {
      long predictionTimeInSeconds = avlParseService.parseAvlTimeAsSeconds(arrivalTime);
      if (lastUpdatedInSeconds != null && predictionTimeInSeconds < lastUpdatedInSeconds) {
        // our prediction is in the past
        return null;
      }
      StopTimeUpdate.Builder stu = StopTimeUpdate.newBuilder();
      try {
        StopTimeEvent.Builder ste = StopTimeEvent.newBuilder();
        ste.setTime(predictionTimeInSeconds);
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
      _log.error("Exception parsing Estimated time " + arrivalTime + " for stop " + stopId, e);
      }
      return stu.build();
  }

}
