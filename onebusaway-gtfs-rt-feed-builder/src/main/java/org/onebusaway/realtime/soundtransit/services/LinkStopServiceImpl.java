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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.StopMapper;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopOffsets;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkStopServiceImpl implements LinkStopService {
  private static Logger _log = LoggerFactory.getLogger(LinkStopServiceImpl.class);


  private StopOffsets stopOffsets = null;
  private StopMapper stopMapper = null;

  public void setStopMapping(Map<String, String> stopMapping) { // For testing
    stopMapper.setStopMapping(stopMapping);
  }

  public void setLinkStopMappingFile(String linkStopMappingFile) {
    stopMapper.setLinkStopMappingFile(linkStopMappingFile);
  }

  public void setNbStopOffsets(List<StopOffset> nbStopOffsets) {
    stopOffsets.setNbStopOffsets(nbStopOffsets);
  }

  public void setSbStopOffsets(List<StopOffset> sbStopOffsets) {

    stopOffsets.setSbStopOffsets(sbStopOffsets);
  }

  @Autowired
  public void setStopOffsets(StopOffsets stopOffsets) {
    this.stopOffsets = stopOffsets;
  }
  public StopOffsets getStopOffsets() {
    return stopOffsets;
  }

  public void updateStopOffsets() {
    stopOffsets.updateStopOffsets(stopMapper);
  }

  @Autowired
  public void setStopMapper(StopMapper stopMapper) {
    this.stopMapper = stopMapper;
  }

  @Override
  public boolean isValidLinkStop(String stopId) {
    boolean isValid = stopMapper.containsKey(stopId);
    return isValid;
  }

  @Override
  public String getGTFSStop(String stopId, String direction) {
    return stopMapper.getGTFSStop(stopId, direction);
  }
  
  @Override
  public List<StopOffset> getStopOffsets(String direction) {
    if (direction.equals("0")) {
      return stopOffsets.getSbStopOffsets();
    } else {
      return stopOffsets.getNbStopOffsets();
    }
  }

  @Override
  public StopUpdate findNextStopOnTrip(StopUpdatesList stopUpdatesList) {
    // Check the times for the StopUpdates to determine which stop the vehicle
    // will reach next. That will be the stop with the earliest estimated
    // arrival time, but an actual time of null. If the trip is already
    // completed, i.e. every stop update has an actual arrival time, then the
    // last stop will be returned.
    stopUpdatesList = (stopUpdatesList == null ? 
        (StopUpdatesList)Collections.EMPTY_LIST : stopUpdatesList); //Check for null
    StopUpdate nextStop = null;
    StopUpdate lastStop = null;
    // Initially, set nextStopTime to an arbitrarily high value.
    Calendar cal = Calendar.getInstance();
    cal.set(2099, 12, 31);
    Date nextStopTime = cal.getTime();
    List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
    if (stopUpdates != null && stopUpdates.size() > 0) {
      for (StopUpdate stopTimeUpdate : stopUpdates) {
        String stopId = stopTimeUpdate.getStopId();
        if (isValidLinkStop(stopId)) {
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
            if (nextStopTime == null || parsedDate.after(nextStopTime)) {
              nextStopTime = parsedDate;
              nextStop = stopTimeUpdate;
            }
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


}
