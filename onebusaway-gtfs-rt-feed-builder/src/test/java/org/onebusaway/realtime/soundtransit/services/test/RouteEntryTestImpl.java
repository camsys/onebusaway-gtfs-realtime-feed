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
}
