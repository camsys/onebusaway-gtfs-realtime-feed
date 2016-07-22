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

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

public class TUFeedBuilderServiceImpl extends FeedBuilderServiceImpl {
  private static Logger _log = LoggerFactory.getLogger(TUFeedBuilderServiceImpl.class);

  private TUFeedBuilderFrequencyServiceImpl _frequencyImpl;
  private TUFeedBuilderScheduleServiceImpl _scheduleImpl;
  
  @Autowired
  public void setTUFeedBuilderFrequencyServiceImpl(TUFeedBuilderFrequencyServiceImpl impl) {
    _frequencyImpl = impl;
  }
  @Autowired
  public void setTUFeedBuilderScheduleServiceImpl(TUFeedBuilderScheduleServiceImpl impl) {
    _scheduleImpl = impl;
  }
  
  @Override
  public FeedMessage buildFrequencyFeedMessage(LinkAVLData linkAVLData) {
    return _frequencyImpl.buildFeedMessage(linkAVLData);
  }

  @Override
  public FeedMessage buildScheduleFeedMessage(LinkAVLData linkAVLData) {
    return _scheduleImpl.buildScheduleFeedMessage(linkAVLData);
  }

}
