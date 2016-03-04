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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvlParseServiceImpl implements AvlParseService {
  private static Logger _log = LoggerFactory.getLogger(AvlParseServiceImpl.class);

  @Override
  public LinkAVLData parseAVLFeed(String feedData) {
    LinkAVLData linkAVLData = new LinkAVLData();
    ObjectMapper mapper = new ObjectMapper().enable(DeserializationConfig
        .Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    mapper.configure(SerializationConfig.Feature.AUTO_DETECT_FIELDS, true);
    boolean parseFailed = false;
    try {
      linkAVLData = mapper.readValue(feedData, new TypeReference<LinkAVLData>(){});
      if (linkAVLData != null) {
        _log.debug("Parsed AVL data: " + mapper.writeValueAsString(linkAVLData));
      }
    } catch (JsonParseException e) {
      _log.error("JsonParseException trying to parse feed data.");
      parseFailed = true;
    } catch (JsonMappingException e) {
      _log.error("JsonMappingException: " + e.getMessage());
      _log.error("AVL feed: " + feedData);
      parseFailed = true;
    } catch (IOException e) {
      _log.error("IOException trying to parse feed data.");
      parseFailed = true;
    } catch (Exception e) {
      _log.error("Exception trying to parse feed data: " + e.getMessage());
      parseFailed = true;
    }
    if (parseFailed) {
      return null;
    }
    // The AVL feed occasionally has dates from 1899.  That is because MySQL
    // will convert a null or zero date to 12-30-1899.
    // Convert any "1899" dates in ArrivalTime to nulls
    TripInfoList tripInfoList = linkAVLData.getTrips();
    if (tripInfoList != null) {
      List<TripInfo> trips = tripInfoList.getTrips();
      if (trips != null) {
        for (TripInfo trip : trips) {
          StopUpdatesList stopUpdatesList = trip.getStopUpdates();
          if (stopUpdatesList == null) {
            continue;
          }
          List<StopUpdate> stopUpdates = stopUpdatesList.getUpdates();
          if (stopUpdates != null && stopUpdates.size() > 0) {
            for (StopUpdate stopTimeUpdate : stopUpdates) {
              ArrivalTime arrivalTime = stopTimeUpdate.getArrivalTime();
              if (arrivalTime != null) {
                String actual = arrivalTime.getActual();
                String estimated = arrivalTime.getEstimated();
                String scheduled = arrivalTime.getScheduled();
                if (actual != null && actual.startsWith("1899")) {
                  arrivalTime.setActual(null);
                }
                if (estimated != null && estimated.startsWith("1899")) {
                  arrivalTime.setEstimated(null);
                }
                if (scheduled != null && scheduled.startsWith("1899")) {
                  arrivalTime.setScheduled(null);
                }
              }
            }
          }
        }
      }
    }
    // Sort StopUpdates in chronological order
    List<TripInfo> trips = null;
    StopUpdatesList stopUpdatesList = null;
    List<StopUpdate> stopUpdates = null;
    if (tripInfoList != null 
        && (trips = tripInfoList.getTrips()) != null) {
      for (TripInfo trip : trips) {
        if ((stopUpdatesList = trip.getStopUpdates()) != null 
            && (stopUpdates  = stopUpdatesList.getUpdates()) != null 
            && stopUpdates.size() > 1) {
          Collections.sort(stopUpdates, new StopUpdateComparator());
        }
      }
    }
    return linkAVLData;
  }

  /*
   * This method will compare two StopUpdates based on their ArrivalTime 
   * information.  It first checks ActualTime, if any, then EstimatedTime,
   * and finally ScheduledTime.
   */
  public class StopUpdateComparator implements Comparator<StopUpdate> {
    @Override
    public int compare(StopUpdate su1, StopUpdate su2) {
      // Check that both updates have ArrivalTime objects
      ArrivalTime arrivalTime1 = su1.getArrivalTime();
      ArrivalTime arrivalTime2 = su2.getArrivalTime();
      long arrival1 = (arrivalTime1 != null) ? 1 : 0;
      long arrival2 = (arrivalTime2 != null) ? 1 : 0;
      if (arrival1 == 0 || arrival2 == 0) {
        return (int)(arrival1 - arrival2);
      }
      
      arrival1 = parseArrivalTime(arrivalTime1.getActual());
      arrival2 = parseArrivalTime(arrivalTime2.getActual());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {  // one is zero, the other isn't
        return (arrival1 > arrival2) ? 0 : 1;  // Non-zero has arrived already
      }
        
      arrival1 = parseArrivalTime(arrivalTime1.getEstimated());
      arrival2 = parseArrivalTime(arrivalTime2.getEstimated());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {
        return (arrival1 > arrival2) ? 0 : 1;
      }
      arrival1 = parseArrivalTime(arrivalTime1.getScheduled());
      arrival2 = parseArrivalTime(arrivalTime2.getScheduled());
      if (arrival1 > 0 && arrival2 > 0) {
        return (arrival1 > arrival2) ? 1 : 0;
      } else if (arrival1 != arrival2) {
        return (arrival1 > arrival2) ? 0 : 1;
      }
      
      return 0;
    }
  }

  /*
   * This method will parse an ArrivalTime string and return 0 if it is null,
   * empty, or cannot be parsed, and will otherwise return the parsed time in
   * milliseconds.
   */
  private long parseArrivalTime(String arrivalTime) {
    long result = 0L;
    if (arrivalTime != null  && !arrivalTime.isEmpty()) {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
      try {
        result = formatter.parse(arrivalTime).getTime();
      } catch (Exception e) {
        result = 0L;
      }
    }
    return result;
  }
}
