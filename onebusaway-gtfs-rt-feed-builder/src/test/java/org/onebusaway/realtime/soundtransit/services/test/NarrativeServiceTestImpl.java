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
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.transit_data_federation.model.ShapePoints;
import org.onebusaway.transit_data_federation.model.narrative.AgencyNarrative;
import org.onebusaway.transit_data_federation.model.narrative.RouteCollectionNarrative;
import org.onebusaway.transit_data_federation.model.narrative.StopNarrative;
import org.onebusaway.transit_data_federation.model.narrative.StopTimeNarrative;
import org.onebusaway.transit_data_federation.model.narrative.TripNarrative;
import org.onebusaway.transit_data_federation.services.narrative.NarrativeService;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;

import java.util.List;

/**
 * Integration test impl of NarrativeService.  Note: only the
 * needed methods are implemented, with the remaining ones
 * throwing UnsupportedOperationException.
 */
public class NarrativeServiceTestImpl implements NarrativeService {

    private GtfsTransitDataServiceFacade _facade;
    public void setFacade(GtfsTransitDataServiceFacade facade) {
        _facade = facade;
    }

    @Override
    public AgencyNarrative getAgencyForId(String agencyId) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public RouteCollectionNarrative getRouteCollectionForId(AgencyAndId routeCollectionId) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public StopNarrative getStopForId(AgencyAndId stopId) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public TripNarrative getTripForId(AgencyAndId tripId) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public StopTimeNarrative getStopTimeForEntry(StopTimeEntry entry) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public ShapePoints getShapePointsForId(AgencyAndId id) {
        ShapePoints sps = new ShapePoints();
        List<ShapePoint> shapePoints = _facade.getShapePointsForId(id);
        sps.setShapeId(id);
        double[] lats = new double[shapePoints.size()];
        double[] lons = new double[shapePoints.size()];
        double[] dist = new double[shapePoints.size()];
        int i = 0;
        for (ShapePoint sp : shapePoints) {
            lats[i] = sp.getLat();
            lons[i] = sp.getLon();
            dist[i] = sp.getDistTraveled();
            i++;
        }
        sps.setLons(lons);
        sps.setLats(lats);
        sps.setDistTraveled(dist);
        return sps;
    }
}
