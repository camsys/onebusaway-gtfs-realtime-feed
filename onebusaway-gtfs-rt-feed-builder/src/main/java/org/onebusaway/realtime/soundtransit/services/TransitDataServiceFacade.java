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
package org.onebusaway.realtime.soundtransit.services;

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Encapsulate all references to TDS in yet another interface
 * so it can be re-implemented for integration tests.
 */
public interface TransitDataServiceFacade {

    List<TripEntry> getAllTrips();
    TripEntry getTripEntryForId(AgencyAndId id);

    Set<Date> getDatesForServiceIds(LocalizedServiceId serviceId);
    boolean areServiceIdsActiveOnServiceDate(LocalizedServiceId serviceId, Date time);
    BlockInstance getBlockInstance(AgencyAndId blockId, long time);
    ScheduledBlockLocation getScheduledBlockLocationFromScheduledTime(BlockConfigurationEntry config, int secondsIntoDay);
    List<Integer> getBlockIds(int route, int blockRunNumber);
    ScheduledBlockLocation getBestScheduledBlockLocationForLocation(BlockInstance block,
                                                                    CoordinatePoint location,
                                                                    long timestamp,
                                                                    double start,
                                                                    double end);
}
