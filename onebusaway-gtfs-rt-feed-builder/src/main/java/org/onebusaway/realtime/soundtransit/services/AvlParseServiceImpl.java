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
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopMapper;
import org.onebusaway.realtime.soundtransit.model.StopOffsets;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatePositionComparator;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class AvlParseServiceImpl implements AvlParseService {
  private static Logger _log = LoggerFactory.getLogger(AvlParseServiceImpl.class);

  public static final String LINK_ROUTE_ID = "100479";
  private String _linkRouteId = LINK_ROUTE_ID;
  private SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSSXXX");
  private StopOffsets _stopOffsets = null;
  private StopMapper _stopMapper = null;
  private TransitDataServiceFacade _tdsf;
  private boolean _testMode = false;
  private boolean _allowUnknownStops = false;

  @Autowired
  public void setTransitDataServiceFacade(TransitDataServiceFacade tdsf) {
     _tdsf = tdsf;
  }

  @Autowired
  public void setStopOffsets(StopOffsets offsets) {
    _stopOffsets = offsets;
  }

  @Autowired
  public void setStopMapper(StopMapper mapper) {
    _stopMapper = mapper;
  }

  public void setTestMode() {
    _testMode = true;
  }

  public void setAllowUnknownStops(boolean allow) {
    _allowUnknownStops = allow;
  }

  @Override
  public String getLinkRouteId() {
    return _linkRouteId;
  }

  public void setLinkRouteId(String id) {
    _linkRouteId = id;
  }

  @PostConstruct
  public void setup() {
    _log.info("setup invoking background thread...");
    // this needs to be on a background thread to allow the bundle to load!
    StartupThread st = new StartupThread(_tdsf, _stopMapper, _stopOffsets);
    new Thread(st).start();
    _log.info("Background thread live!");
  }

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
      _log.error("JsonParseException trying to parse feed data.", e);
      parseFailed = true;
    } catch (JsonMappingException e) {
      _log.error("JsonMappingException: " + e.getMessage());
      _log.error("AVL feed: " + feedData);
      parseFailed = true;
    } catch (IOException e) {
      _log.error("IOException trying to parse feed data:", e);
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
    // Sort StopUpdates in stop order
    List<TripInfo> trips = null;
    StopUpdatesList stopUpdatesList = null;
    List<StopUpdate> stopUpdates = null;
    if (tripInfoList != null
        && (trips = tripInfoList.getTrips()) != null) {
      for (TripInfo trip : trips) {
        if ((stopUpdatesList = trip.getStopUpdates()) != null 
            && (stopUpdates  = stopUpdatesList.getUpdates()) != null 
            && stopUpdates.size() > 1) {
          if (!_testMode) {
            if (_log.isDebugEnabled())
              _log.debug("Pre sort["+ trip.getTripId() + ":" + trip.getDirection() + "]:" + stopUpdates);
            Collections.sort(stopUpdates, new StopUpdatePositionComparator(_stopMapper, _stopOffsets, trip.getDirection(), _allowUnknownStops));
            if (_log.isDebugEnabled())
              _log.debug("Post sort["+ trip.getTripId() + ":" + trip.getDirection() + "]:" + stopUpdates);
          } else {
            _log.warn("test mode:  stop sorting disabled");
          }
        }
      }
    }
    return linkAVLData;
  }

  // vehicle ids now AVL trips, no hashing required
  @Override
  public String hashVehicleId(String vehicleId) {
    return vehicleId;
  }

@Override
  /*
  * look for predictions (null actual arrival times)
   */
  public boolean hasPredictions(TripInfo trip) {
    long lastUpdated = parseAvlTimeAsMillis(trip.getLastUpdatedDate());
    boolean foundNullSchedule = false;
    if (trip != null && trip.getStopUpdates() != null && trip.getStopUpdates().getUpdates() != null) {
      for (StopUpdate su : trip.getStopUpdates().getUpdates()) {
        if (su.getArrivalTime().getActual() == null) {
          if (lastUpdated == 0) {
            // for whatever reason we don't have a date to compare to, assume valid
            foundNullSchedule = true;
            break;
          } else {
            long estimated = parseAvlTimeAsMillis(su.getArrivalTime().getEstimated());
            if (estimated >= lastUpdated) {
              // prediction is in the future, keep it
              foundNullSchedule = true;
            }
          }
        }
      }
    }
    return foundNullSchedule;
}



  public long parseAvlTimeAsMillis(String arrivalTime) {
    Date d = parseAvlTime(arrivalTime);
    if (d == null) return 0L;
    return d.getTime();  
  }

  public long parseAvlTimeAsSeconds(String arrivalTime) {
    long l = parseAvlTimeAsMillis(arrivalTime);
    if (l == 0L) return l;
    return TimeUnit.SECONDS.convert(l, TimeUnit.MILLISECONDS);  
  }

  
  /*
   * This method will parse an ArrivalTime string and return 0 if it is null,
   * empty, or cannot be parsed, and will otherwise return the parsed time in
   * milliseconds.
   */
  public Date parseAvlTime(String arrivalTime) {
    Date result = null;
    if (arrivalTime != null  && !arrivalTime.isEmpty()) {
      try {
        result = FORMATTER.parse(arrivalTime);
      } catch (Exception e) {
        result = null;
      }
    }
    return result;
  }
  
  public String formatAvlTime(Date d) {
    return FORMATTER.format(d);
  }

  /**
   * poll the TDS to see if the bundle has been loaded
   * but do it off the context init thread so the bundle can be
   * loaded in the background.
   */
  private static class StartupThread implements Runnable {

    private TransitDataServiceFacade tdsf;
    private StopMapper stopMapper;
    private StopOffsets stopOffsets;

    public StartupThread(TransitDataServiceFacade tdsf, StopMapper stopMapper, StopOffsets stopOffsets) {
      this.tdsf = tdsf;
      this.stopMapper = stopMapper;
      this.stopOffsets = stopOffsets;
    }

    @Override
    public void run() {
      int tripCount = getTripCount();

      while (!Thread.interrupted()
              && tripCount == 0) {
        try {
          _log.info("stop offset load waiting on bundle....");
          Thread.currentThread().sleep(5 * 1000);
          tripCount = getTripCount();

        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      _log.info("loading stop offsets!");
      stopOffsets.updateStopOffsets(stopMapper);
    }

    private int getTripCount() {
      int tripCount = 0;
      try {
        if (tdsf.getAllTrips() != null)
          tripCount = tdsf.getAllTrips().size();
      } catch (Exception any) {
        // bury
      }
      return tripCount;
    }
  }

}
