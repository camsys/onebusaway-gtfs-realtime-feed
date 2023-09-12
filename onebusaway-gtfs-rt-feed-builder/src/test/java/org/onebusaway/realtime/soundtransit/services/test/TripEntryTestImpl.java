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

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.RouteCollectionEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.RouteEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration test impl of TripEntry.  Note: only the needed
 * methods are implemented with the remaining ones throwing
 * UnsupoortedOperationException.
 */
public class TripEntryTestImpl implements TripEntry {
    private AgencyAndId _id;
    @Override
    public AgencyAndId getId() {
        return _id;
    }
    public void setId(AgencyAndId id) {
        _id = id;
    }

    private RouteEntry _route = null;
    @Override
    public RouteEntry getRoute() {
        return _route;
    }
    public void setRouteEntry(RouteEntry entry) {
        _route = entry;
    }

    @Override
    public RouteCollectionEntry getRouteCollection() {
        throw new UnsupportedOperationException("not implemented");
    }

    String _directionId;
    @Override
    public String getDirectionId() {
        return _directionId;
    }
    public void setDirectionId(String id) {
        _directionId = id;
    }

    BlockEntry _block;
    @Override
    public BlockEntry getBlock() {
        return _block;
    }
    public TripEntry setBlock(BlockEntry block) {
        _block = block;
        return null;
    }

    LocalizedServiceId _serviceId;
    @Override
    public LocalizedServiceId getServiceId() {
        return _serviceId;
    }
    public void setServiceId(LocalizedServiceId id) {
        _serviceId = id;
    }

    private AgencyAndId _shapeId = null;
    @Override
    public AgencyAndId getShapeId() {
        return _shapeId;
    }
    public void setShapeId(AgencyAndId id) {
        _shapeId = id;
    }
    List<StopTimeEntry> _stopTimes = new ArrayList<StopTimeEntry>();
    @Override
    public List<StopTimeEntry> getStopTimes() {
        return _stopTimes;
    }
    public void setStopTimes(List<StopTimeEntry> stopTimes) {
        _stopTimes = stopTimes;
    }

    double _totalTripDistance;
    @Override
    public double getTotalTripDistance() {
        return _totalTripDistance;
    }
    public void setTotalTripDistance(double d) {
        _totalTripDistance = d;
    }

    @Override
    public FrequencyEntry getFrequencyLabel() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString() {
        if (_id == null) return "NuLl";
        return _id.toString();
    }
}
