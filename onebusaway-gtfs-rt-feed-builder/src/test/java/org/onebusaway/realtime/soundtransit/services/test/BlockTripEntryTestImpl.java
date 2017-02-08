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

import org.onebusaway.transit_data_federation.services.blocks.AbstractBlockTripIndex;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockStopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockTripEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.List;

/**
 * Integration test impl of BlockTripEntry.  Note: only needed
 * methods are implemented, with the remaining throwing
 * UnsupportedOperationException.
 */
public class BlockTripEntryTestImpl implements BlockTripEntry {
    BlockConfigurationEntry _bce;
    @Override
    public BlockConfigurationEntry getBlockConfiguration() {
        return _bce;
    }

    private TripEntry _trip;
    @Override
    public TripEntry getTrip() {
        return _trip;
    }
    public void setTrip(TripEntry trip) {
        _trip = trip;
    }

    @Override
    public short getSequence() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public short getAccumulatedStopTimeIndex() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int getAccumulatedSlackTime() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public double getDistanceAlongBlock() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public BlockTripEntry getPreviousTrip() {
        throw new UnsupportedOperationException("not implemented");
    }

    BlockTripEntry _nextTrip;
    @Override
    public BlockTripEntry getNextTrip() {
        return _nextTrip;
    }
    public void setNextTrip(BlockTripEntry entry) {
        _nextTrip = entry;
    }

    @Override
    public int getArrivalTimeForIndex(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int getDepartureTimeForIndex(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public double getDistanceAlongBlockForIndex(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public AbstractBlockTripIndex getPattern() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<BlockStopTimeEntry> getStopTimes() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString() {
        if (_trip == null) return "NuLl";
        return _trip.toString();
    }

}
