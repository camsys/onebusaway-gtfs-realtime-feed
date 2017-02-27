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

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Maps AVL Stops to GTFS stops and back, handling exceptions such as
 * platform stops that may have several gtfs stop ids.
 */
public class StopMapper {

    private static Logger _log = LoggerFactory.getLogger(StopMapper.class);

    private static String _linkStopMappingFile;
    private Map<String, String> stopMapping = null; // Link id, Gtfs id
    private Map<String, String> platformMap = null; // Gtfs id, Link platform name
    // platform stops have two gtfs ids that stopMapping doesn't directly support
    private Map<String, String> platformDirectionMap = null; // gtfsId_gtfsDirection, alternate gtfs id
    private boolean allowUnknownStops = false;

    public void setAllowUnknownStops(boolean allow) {
        allowUnknownStops = allow;
    }
    public void setStopMapping(Map<String, String> stopMapping) {
        this.stopMapping = stopMapping;
    }

    public void setLinkStopMappingFile(String file) {
        _linkStopMappingFile = file;
    }
    // easy testing support of stop pairs
    public void addPlatformDirection(String stopAndDirection, String gtfsStopId) {
        if (platformDirectionMap == null) platformDirectionMap = new HashMap<String, String>();
        platformDirectionMap.put(stopAndDirection, gtfsStopId);
    }

    @PostConstruct
    public void init() {
    // Read in the AVL-GTFS stop mapping file
    try {
            _log.info("loading " + _linkStopMappingFile);
            stopMapping = parseCSVToMap(_linkStopMappingFile);
            platformMap = parseCSVToMap(_linkStopMappingFile + ".alias");
            platformDirectionMap = parseCSVToMap(_linkStopMappingFile + ".pairs");
        } catch(IOException e) {
            _log.error("Error reading StopMapping file " + e.getMessage());
        }
        if (stopMapping.isEmpty())
            throw new IllegalStateException("missing StopMappingFile configured at " + _linkStopMappingFile);
        if (platformMap == null || platformMap.isEmpty()) {
            throw new IllegalStateException("missing platform map configured at " + _linkStopMappingFile + ".alias");
        }
        if (platformDirectionMap == null || platformDirectionMap.isEmpty()) {
            throw new IllegalStateException("missing platform direction map configured at " + _linkStopMappingFile + ".pairs");
        }
    }

    public boolean containsKey(String linkStopId) {
        return stopMapping.containsKey(linkStopId);
    }

    public Map<String, String> parseCSVToMap(String filename) throws IOException {
        Map<String, String> csv = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String ln = "";
        while ((ln = br.readLine()) != null) {
            _log.debug(ln);
            int idx = ln.indexOf(',');
            // support comments, accept only comma delimiters
            if (idx > 0 && '#' != ln.toCharArray()[0]) {
                csv.put(ln.substring(0, idx), ln.substring(idx + 1));
            }
        }
        return csv;
    }

    public String getGTFSStop(String stopId, String direction) {
        String mappedStopId = "";
        if (stopId != null) {
            mappedStopId = stopMapping.get(stopId);
        }

        if (mappedStopId != null && !mappedStopId.isEmpty()) {
            // Check for special case at platforms where northbound
            // and southbound trains have an AVL stop id of "XXX_PLAT".
            // 0 is south, 1 is north
            if (platformDirectionMap.containsKey(stopDirection(mappedStopId, direction))) {
                _log.info("swapped stopPair " + mappedStopId + " for "
                + platformDirectionMap.get(stopDirection(mappedStopId, direction)));
                mappedStopId = platformDirectionMap.get(stopDirection(mappedStopId, direction));
            }
        }
        return mappedStopId;

    }

    private String stopDirection(String gtfsStopId, String gtfsDirection) {
        return gtfsStopId + "_" + gtfsDirection;
    }

    public String getAVLStopId(String gtfsStopId, String gtfsDirection) {
        String result = "";

        // platform stops often have duplicate gtfs ids
        if (platformMap.containsKey(gtfsStopId)) {
            return platformMap.get(gtfsStopId);
        }
        Iterator it = stopMapping.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> stopPair = (Map.Entry)it.next();
            if (stopPair.getValue().equals(gtfsStopId)) {
                result = stopPair.getKey();
                break;
            }
        }
        // if configured no unknown stops are allowed
        if ("".equals(result) && !allowUnknownStops)
            throw new IllegalStateException("unknown avl stop for gtfs stopid ("
                    + gtfsDirection + ") "
                    + gtfsStopId
            + "\n and platformMap=" + platformMap);
        return result;
    }
}
