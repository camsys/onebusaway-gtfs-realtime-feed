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
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkStopServiceImpl implements LinkStopService {
  private static Logger _log = LoggerFactory.getLogger(LinkStopServiceImpl.class);
  private Map<String, String> stopMapping = null; // Link id, Gtfs id
  private static String _linkStopMappingFile;
  private List<StopOffset> nbStopOffsets = new ArrayList<StopOffset>();// Northbound
  private List<StopOffset> sbStopOffsets = new ArrayList<StopOffset>();// Southbound

  public void setStopMapping(Map<String, String> stopMapping) { // For testing
    this.stopMapping = stopMapping;
  }

  public void setLinkStopMappingFile(String linkStopMappingFile) {
    _linkStopMappingFile = linkStopMappingFile;
  }

  public void setNbStopOffsets(List<StopOffset> nbStopOffsets) {
    this.nbStopOffsets = nbStopOffsets;
  }

  public void setSbStopOffsets(List<StopOffset> sbStopOffsets) {
    this.sbStopOffsets = sbStopOffsets;
  }

  @PostConstruct
  public void init() {
    // Read in the AVL-GTFS stop mapping file
    try (BufferedReader br = new BufferedReader(new FileReader(_linkStopMappingFile))) {
      stopMapping = new HashMap<String, String>();
      String ln = "";
      while ((ln = br.readLine()) != null) {
        _log.info(ln);
        int idx = ln.indexOf(',');
        if (idx > 0) {
          stopMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
        }
      }
    } catch (IOException e) {
      _log.error("Error reading StopMapping file " + e.getMessage());
    }
  }
  
  @Override
  public boolean isValidLinkStop(String stopId) {
    boolean isValid = stopMapping.containsKey(stopId);    
    return isValid;
  }

  @Override
  public String getGTFSStop(String stopId, String direction) {
    String mappedStopId = "";
    if (stopId != null) {
      mappedStopId = stopMapping.get(stopId);
    }

    if (mappedStopId != null && !mappedStopId.isEmpty()) {
      // Check for special case at Sea-Tac Airport, where both northbound 
      // and southbound trains have an AVL stop id of "SEA_PLAT".
      if (mappedStopId.equals("99903") || mappedStopId.equals("99904")) {
        if (direction == "0") {
          mappedStopId = "99904";
        } else {
          mappedStopId = "99903";
        }
      }
    }
    return mappedStopId;
  }
  
  @Override
  public List<StopOffset> getStopOffsets(String direction) {
    if (direction.equals("0")) {
      return sbStopOffsets;
    } else {
      return nbStopOffsets;
    }
  }

  @Override
  public StopUpdate findNextStopOnTrip(StopUpdatesList stopUpdatesList) {
    // Check the times for the StopUpdates to determine which stop the vehicle
    // will reach next. That will be the stop with the earliest estimated
    // arrival time, but an actual time of null. If the trip is already
    // completed, i.e. every stop update has an actual arrival time, then an
    // empty string will be returned.
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
            if (parsedDate.before(nextStopTime)) {
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

  public void updateStopOffsets(List<TripEntry> tripEntries) {
    // Create tables of northbound and southbound stop offsets
    int sbStopCt = 0;   // Direction = "0"
    int nbStopCt = 0;   // Direction = "1"
    TripEntry sbTrip = null;
    TripEntry nbTrip = null;
    for (TripEntry trip : tripEntries) {
      List<StopTimeEntry> stopTimeEntries = trip.getStopTimes();
      if (trip.getDirectionId().equals("0") && stopTimeEntries.size() > sbStopCt) {
        sbTrip = trip;
        sbStopCt = stopTimeEntries.size();
      } else if (trip.getDirectionId().equals("1") && stopTimeEntries.size() > nbStopCt) {
        nbTrip = trip;
        nbStopCt = stopTimeEntries.size();
      }
    }
    if (sbStopCt == 0) {
      _log.error("No southbound stops found for this route");
    }
    if (nbStopCt == 0) {
      _log.error("No northbound stops found for this route");
    }
    if (sbTrip != null && sbTrip.getId() != null)
    _log.info("Southbound trip " + sbTrip.getId().toString() + " has "
        + sbStopCt + " stops.");
    if (nbTrip != null && nbTrip.getId() != null)
    _log.info("Northbound trip " + nbTrip.getId().toString() + " has "
        + nbStopCt + " stops.");
    List<StopTimeEntry> sbStopTimeEntries = new ArrayList<StopTimeEntry>();
    if (sbTrip != null) {
      sbStopTimeEntries = sbTrip.getStopTimes();
    }
    sbStopOffsets.clear();
    nbStopOffsets.clear();
    for (StopTimeEntry stopTimeEntry : sbStopTimeEntries) {
      String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
      String avlStopId = getAVLStopId(gtfsStopId);
      int arrivalTime = stopTimeEntry.getArrivalTime();
      _log.info("GTFS/AVL id: " + gtfsStopId + " / " + avlStopId);
      sbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, "0", arrivalTime));
    }
    List<StopTimeEntry> nbStopTimeEntries = new ArrayList<StopTimeEntry>();
    if (nbTrip != null) {
      nbTrip.getStopTimes();
    }
    for (StopTimeEntry stopTimeEntry : nbStopTimeEntries) {
      String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
      String avlStopId = getAVLStopId(gtfsStopId);
      int arrivalTime = stopTimeEntry.getArrivalTime();
      _log.info("GTFS/AVL id: " + gtfsStopId + " / " + avlStopId);
      nbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, "1", arrivalTime));
    }
    _log.info("***LinkStopService***");
    if (sbStopOffsets.size() > 0 && nbStopOffsets.size() > 0) {
      Collections.sort(sbStopOffsets, new StopOffsetComparator());
      // Adjust offsets, setting first stop to zero and adjusting the others
      // accordingly.
      int offsetAdjustment = sbStopOffsets.get(0).getOffset();
      for (StopOffset so : sbStopOffsets) {
        so.setOffset(so.getOffset() - offsetAdjustment);
        _log.info(so.toString());
      }
      Collections.sort(nbStopOffsets, new StopOffsetComparator());
      // Adjust offsets, setting first stop to zero and adjusting the others
      // accordingly.
      offsetAdjustment = nbStopOffsets.get(0).getOffset();
      for (StopOffset so : nbStopOffsets) {
        so.setOffset(so.getOffset() - offsetAdjustment);
        _log.info(so.toString());
      }
    }
    return;
  }
  
  private String getAVLStopId(String gtfsStopId) {
    String result = "";
    if (gtfsStopId.equals("99903") || gtfsStopId.equals("99904")) {
      result = "SEA_PLAT";
    } else {
      Iterator it = stopMapping.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> stopPair = (Map.Entry)it.next();
        if (stopPair.getValue().equals(gtfsStopId)) {
          result = stopPair.getKey();
          break;
        }
      }
    }
    return result;
  }
  
  private class StopOffsetComparator implements Comparator<StopOffset> {
    // Compare StopOffsets based on their offset values.
    @Override
    public int compare(StopOffset so1, StopOffset so2) {
      return so1.getOffset() - so2.getOffset();
    }
  }
}
