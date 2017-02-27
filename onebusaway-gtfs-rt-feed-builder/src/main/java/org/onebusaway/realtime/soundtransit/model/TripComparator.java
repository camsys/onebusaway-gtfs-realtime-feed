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

import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.Comparator;
import java.util.List;

/**
 * Sort trips based on trip start time
 */
public class TripComparator implements Comparator<TripEntry> {

    @Override
    public int compare(TripEntry t1, TripEntry t2) {
        // Compare direction
        if (!t1.getDirectionId().equals(t2.getDirectionId())) {
            if (t1.getDirectionId().equals("0")) {    // Outbound, to the airport
                return -1;
            } else {
                return 1;
            }
        }
        // Compare trip start times
        int time1 = getTripStartTimeInSecs(t1);
        int time2 = getTripStartTimeInSecs(t2);
        if (time1 != time2) {
            if (time1 < time2) {
                return -1;
            } else {
                return 1;
            }
        }
        return 0;
    }


    // Returns the start time in seconds of the trip
    public int getTripStartTimeInSecs(TripEntry trip) {
        int startTime = 0;
        List<BlockConfigurationEntry> blocks = trip.getBlock().getConfigurations();
        if (blocks == null) {
            throw new NullPointerException("block has no configurations");
        }
        if (blocks.size() > 0) {
            List<FrequencyEntry> frequencies = blocks.get(0).getFrequencies();
            if (frequencies != null && frequencies.size() > 0) {
                startTime = frequencies.get(0).getStartTime();
            } else {
                startTime = blocks.get(0).getArrivalTimeForIndex(0);
            }
        }
        return startTime;
    }

}

