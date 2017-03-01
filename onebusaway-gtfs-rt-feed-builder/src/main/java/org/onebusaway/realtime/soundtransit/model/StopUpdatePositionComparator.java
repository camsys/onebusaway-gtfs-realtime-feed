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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * sort based on the position of the stop
 * along the trip (assuming we can imply the direction)
 */
public class StopUpdatePositionComparator implements Comparator<StopUpdate> {

    private static Logger _log = LoggerFactory.getLogger(StopUpdatePositionComparator.class);
    private StopOffsets _offsets;
    private StopMapper _mapper;
    private String _direction;
    private boolean _allowUnknownStops;
    public StopUpdatePositionComparator(StopMapper mapper, StopOffsets offsets, String direction, boolean allowUnknownStops) {
        _mapper = mapper;
        _offsets = offsets;
        _direction = direction;
        _allowUnknownStops = allowUnknownStops;
    }
    @Override
    public int compare(StopUpdate su1, StopUpdate su2) {
        return compareTo(su1.getStopId(), su2.getStopId());
    }

    public int compareTo(String stopId1, String stopId2) {
        return compareTo(_direction, getPositionOfStop(stopId1), getPositionOfStop(stopId2));
    }
    public int compareTo(String direction, int pos1, int pos2) {
        // it turns out direction isn't important, stop positions imply this
        return Integer.compare(pos1, pos2);
    }

    public boolean isNorthBound(String direction) {
        if ("N".equals(direction) || "1".equals(direction)) {
            return true;
        }
        return false;
    }

    private Map<String, Integer> cache = new HashMap<String, Integer>();
    public int getPositionOfStop(String stopId) {
        if (!cache.containsKey(hash(stopId))) {
            cache.put(hash(stopId), countIndex(stopId));
        }
        _log.debug("position(" + stopId + ")=" + cache.get(hash(stopId)));
        return cache.get(hash(stopId));
    }

    public String hash(String stopId) {
        return _direction + "_" + stopId;
    }

    public int countIndex(String avlStopId) {
        _log.debug("countIndex(" + avlStopId + ")(" + _direction + ")");
        if (isNorthBound(_direction)) {
            for (StopOffset nb : _offsets.getNbStopOffsets()) {
                if (avlStopId.equals(nb.getLinkStopId()))
                    return nb.getOffset();
            }
        } else {
            for (StopOffset sb : _offsets.getSbStopOffsets()) {
                if (avlStopId.equals(sb.getLinkStopId()))
                    return sb.getOffset();
            }
        }
        // now we check to see if the train is on the wrong track!
        if (!isNorthBound(_direction)) {
            for (StopOffset nb : _offsets.getNbStopOffsets()) {
                if (avlStopId.equals(nb.getLinkStopId())) {
                    _log.info("stop " + avlStopId + " on wrong direction (" + _direction + ")");
                    return nb.getOffset();
                }
            }
        } else {
            for (StopOffset sb : _offsets.getSbStopOffsets()) {
                if (avlStopId.equals(sb.getLinkStopId())) {
                    _log.info("stop " + avlStopId + " on wrong direction (" + _direction + ")");
                    return sb.getOffset();
                }

            }
        }
        if (!_allowUnknownStops) {
            throw new IllegalStateException(("unknown stop |" + avlStopId
                    + "| with direction=" + _direction
                    + " and offsets=" + _offsets));
        }
        // we don't know
        return 0;
    }
}
