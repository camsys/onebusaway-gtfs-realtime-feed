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
import org.onebusaway.transit_data_federation.services.transit_graph.RouteCollectionEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.RouteEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.List;

/**
 * Testing impl of RouteEntry
 */
public class RouteEntryTestImpl implements RouteEntry {

    private AgencyAndId id;
    @Override
    public AgencyAndId getId() {
        return id;
    }

    public void setId(AgencyAndId id) {
        this.id = id;
    }

    @Override
    public RouteCollectionEntry getParent() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TripEntry> getTrips() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int getType() {
        return 0;
    }
}
