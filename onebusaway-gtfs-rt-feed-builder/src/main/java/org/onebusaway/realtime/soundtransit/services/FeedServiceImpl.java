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

import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;

@Component
/**
 * Maps the GTFS-realtime protocol buffer models to the archiver models.
 * 
 */
public class FeedServiceImpl implements FeedService {
  private static Logger _log = LoggerFactory.getLogger(FeedServiceImpl.class);
  private AvlParseService _avlParseService;
  private FeedBuilderService _vpFeedBuilderServiceImpl;
  private FeedBuilderService _tuFeedBuilderServiceImpl;
  private FeedMessage _currentVehiclePositions;
  private FeedMessage _currentTripUpdates;
  private boolean _isFrequencyBased = false;

  @Autowired
  public void setAvlParseService(AvlParseService avlParseService) {
    _avlParseService = avlParseService;
  }

  @Autowired
  public void setVpFeedBuilderServiceImpl(
      FeedBuilderService vpFeedBuilderServiceImpl) {
    _vpFeedBuilderServiceImpl = vpFeedBuilderServiceImpl;
  }

  @Autowired
  public void setTuFeedBuilderServiceImpl(
      FeedBuilderService tuFeedBuilderServiceImpl) {
    _tuFeedBuilderServiceImpl = tuFeedBuilderServiceImpl;
  }

  public void setFrequencySchedule(String isFrequency) {
    _isFrequencyBased = "true".equalsIgnoreCase(isFrequency);
  }
  
  public boolean isFrequencySchedule() {
    return _isFrequencyBased;
  }
  
  @Override
  public FeedMessage getCurrentVehiclePositions() {
    return _currentVehiclePositions;
  }

  public void setCurrentVehiclePositions(FeedMessage currentVehiclePositions) {
    this._currentVehiclePositions = currentVehiclePositions;
  }

  @Override
  public FeedMessage getCurrentTripUpdates() {
    return _currentTripUpdates;
  }

  public void setCurrentTripUpdates(FeedMessage currentTripUpdates) {
    this._currentTripUpdates = currentTripUpdates;
  }

  @Override
  public LinkAVLData parseAVLFeed(String feedData) {
    return _avlParseService.parseAVLFeed(feedData);
  }
  
  @Override
  public FeedMessage buildVPMessage(LinkAVLData linkAVLData) {
    FeedMessage vehiclePositionsFM = null;
    if (isFrequencySchedule()) {
      vehiclePositionsFM = _vpFeedBuilderServiceImpl.buildFrequencyFeedMessage(linkAVLData);
    } else {
      vehiclePositionsFM = _vpFeedBuilderServiceImpl.buildScheduleFeedMessage(linkAVLData);
    }
    if (vehiclePositionsFM != null) {
      _currentVehiclePositions = vehiclePositionsFM;
    }
    return vehiclePositionsFM;
  }

  @Override
  public FeedMessage buildTUMessage(LinkAVLData linkAVLData) {
    FeedMessage tripUpdatesFM = null;
    if (this.isFrequencySchedule()) {
      tripUpdatesFM = _tuFeedBuilderServiceImpl.buildFrequencyFeedMessage(linkAVLData);
    } else {
      tripUpdatesFM = _tuFeedBuilderServiceImpl.buildScheduleFeedMessage(linkAVLData);
    }
    if (tripUpdatesFM != null) {
      _currentTripUpdates = tripUpdatesFM;
    }
    return tripUpdatesFM;
  }
}
