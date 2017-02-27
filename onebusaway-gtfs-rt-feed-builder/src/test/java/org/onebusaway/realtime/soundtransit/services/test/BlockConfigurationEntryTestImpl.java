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
package org.onebusaway.realtime.soundtransit.services.test;

import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockStopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockTripEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.ServiceIdActivation;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration test implementation of BlockConfiguration.  Note only the
 * methods needed are implemented with remaining ones throwing
 * UnsupportedOperationExceptions.
 */
public class BlockConfigurationEntryTestImpl implements BlockConfigurationEntry {
    @Override
    public BlockEntry getBlock() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ServiceIdActivation getServiceIds() {
        throw new UnsupportedOperationException("not implemented");
    }

    private List<BlockTripEntry> _trips = new ArrayList<BlockTripEntry>();
    @Override
    public List<BlockTripEntry> getTrips() {
        return _trips;
    }

    private List<FrequencyEntry> _frequencies = new ArrayList<FrequencyEntry>();
    @Override
    public List<FrequencyEntry> getFrequencies() {
        return _frequencies;
    }
    public void setFrequencies(List<FrequencyEntry> f) {
        _frequencies = f;
    }

    @Override
    public double getTotalBlockDistance() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public double getDistanceAlongBlockForIndex(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int getArrivalTimeForIndex(int i) {
        return _stopTimes.get(i).getStopTime().getArrivalTime();
    }

    @Override
    public int getDepartureTimeForIndex(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    List<BlockStopTimeEntry> _stopTimes = new ArrayList<BlockStopTimeEntry>();
    @Override
    public List<BlockStopTimeEntry> getStopTimes() {
        return _stopTimes;
    }
}
