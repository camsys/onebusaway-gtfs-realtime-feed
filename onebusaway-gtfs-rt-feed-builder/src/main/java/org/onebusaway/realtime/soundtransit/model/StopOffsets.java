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
package org.onebusaway.realtime.soundtransit.model;

import org.onebusaway.realtime.soundtransit.services.TransitDataServiceFacade;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.onebusaway.realtime.soundtransit.services.AvlParseServiceImpl.LINK_ROUTE_ID;

/**
 * Manage list of Stop offsets -- essentially the gtfs sequence order/id.
 * These stop offsets can be used to determine the order of the stops.  @see
 * StopUpdatePositionComparator.
 */
public class StopOffsets {

    private static Logger _log = LoggerFactory.getLogger(StopOffsets.class);

    private TransitDataServiceFacade _tdsf;
    private List<StopOffset> nbStopOffsets = new ArrayList<StopOffset>();// Northbound
    private List<StopOffset> sbStopOffsets = new ArrayList<StopOffset>();// Southbound
    private String _linkRouteId = LINK_ROUTE_ID;

    @Autowired
    public void setTransitDataServiceFacade(TransitDataServiceFacade tds) {
        _tdsf = tds;
    }

    public void setNbStopOffsets(List<StopOffset> nbStopOffsets) {
        // this is for testing only!
        _log.info("nb stop offsets override!");
        this.nbStopOffsets = nbStopOffsets;
    }
    public List<StopOffset> getNbStopOffsets() { return nbStopOffsets; }
    public int getNbSize() { return nbStopOffsets.size(); }
    public void setSbStopOffsets(List<StopOffset> sbStopOffsets) {
        // this is for testing only!
        _log.info("sb stop offsets override!=" + sbStopOffsets);
        this.sbStopOffsets = sbStopOffsets;
    }
    public List<StopOffset> getSbStopOffsets() { return sbStopOffsets; }
    public int getSbSize() { return sbStopOffsets.size(); }
    public void setLinkRouteId(String id) {
        _linkRouteId = id;
    }

    public void clear() {
        sbStopOffsets.clear();
        nbStopOffsets.clear();
    }

    public String getLinkRouteId() {
        return _linkRouteId;
    }

    /**
     * this returns ALL Trips for LINK route.  It does not consider active service ids
     * as it cannot know the current service date.
     * @return
     */
    public List<TripEntry> getLinkTrips() {
        _log.info("getLinkTrips() with tds=" + _tdsf);
        List<TripEntry> allTrips = _tdsf.getAllTrips();
        _log.info("found " + allTrips.size() + " potential trips");
        List<TripEntry> linkTrips = new ArrayList<TripEntry>();
        for (TripEntry trip : allTrips) {
            if (trip.getRoute().getId().getId().equals(getLinkRouteId())) {
                linkTrips.add(trip);
            }
        }
        TripComparator tripComparator = new TripComparator();
        Collections.sort(linkTrips, tripComparator);

        return linkTrips;
    }


    public void addStopOffset(String gtfsStopId, String avlStopId, String direction, int arrivalTime) {
        _log.debug("add(gtfs=" + gtfsStopId
        + ", avl=" + avlStopId
        + ", direction=" + direction
        + ", arrivalTime=" + arrivalTime);

        if ("0".equals(direction)) {
            sbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, direction, arrivalTime));
        } else {
            nbStopOffsets.add(new StopOffset(gtfsStopId, avlStopId, direction, arrivalTime));
        }
    }

    public void updateStopOffsets(StopMapper stopMapper) {
        _log.info("update called with tds=" + _tdsf + " and stopMapper=" + stopMapper);
        // Create tables of northbound and southbound stop offsets
        int sbStopCt = 0;   // Direction = "0"
        int nbStopCt = 0;   // Direction = "1"
        TripEntry sbTrip = null;
        TripEntry nbTrip = null;
        for (TripEntry trip : getLinkTrips()) {
            // here we loop through all trips to find the one with the most stops (nb and sb)
            _log.debug("looking at trip=" + trip.getId() + " with direction=" + trip.getDirectionId());
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
        clear();
        for (StopTimeEntry stopTimeEntry : sbStopTimeEntries) {
            String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
            String avlStopId = stopMapper.getAVLStopId(gtfsStopId, "0");
            int arrivalTime = stopTimeEntry.getArrivalTime();
            _log.debug("sb GTFS/AVL id: " + gtfsStopId + " / " + avlStopId + " / " + arrivalTime);
            addStopOffset(gtfsStopId, avlStopId, "0", arrivalTime);
        }
        List<StopTimeEntry> nbStopTimeEntries = new ArrayList<StopTimeEntry>();
        if (nbTrip != null) {
            nbStopTimeEntries = nbTrip.getStopTimes();
        }
        for (StopTimeEntry stopTimeEntry : nbStopTimeEntries) {
            String gtfsStopId = stopTimeEntry.getStop().getId().getId().toString();
            String avlStopId = stopMapper.getAVLStopId(gtfsStopId, "1");
            int arrivalTime = stopTimeEntry.getArrivalTime();
            _log.debug("nb GTFS/AVL id: " + gtfsStopId + " / " + avlStopId + " / " + arrivalTime);
            addStopOffset(gtfsStopId, avlStopId, "1", arrivalTime);
        }

        if (getSbSize() > 0 && getNbSize() > 0) {
            Collections.sort(getSbStopOffsets(), new StopOffsetComparator());
            // Adjust offsets, setting first stop to zero and adjusting the others
            // accordingly.
            int offsetAdjustment = getSbStopOffsets().get(0).getOffset();
            for (StopOffset so : getSbStopOffsets()) {
                so.setOffset(so.getOffset() - offsetAdjustment);
                _log.debug(so.toString());
            }
            Collections.sort(getNbStopOffsets(), new StopOffsetComparator());
            // Adjust offsets, setting first stop to zero and adjusting the others
            // accordingly.
            offsetAdjustment = getNbStopOffsets().get(0).getOffset();
            for (StopOffset so : getNbStopOffsets()) {
                so.setOffset(so.getOffset() - offsetAdjustment);
                _log.debug(so.toString());
            }
        }
        return;
    }

    public String toString() {
        return "{nb="
                + nbStopOffsets
                + ", sb="
                + sbStopOffsets
                + "}";
    }

    private class StopOffsetComparator implements Comparator<StopOffset> {
        // Compare StopOffsets based on their offset values.
        @Override
        public int compare(StopOffset so1, StopOffset so2) {
            return so1.getOffset() - so2.getOffset();
        }
    }
}
