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
import org.onebusaway.transit_data_federation.services.ExtendedCalendarService;
import org.onebusaway.transit_data_federation.services.blocks.BlockCalendarService;
import org.onebusaway.transit_data_federation.services.blocks.BlockGeospatialService;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.BlockRunService;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocationService;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.ServiceIdActivation;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * TransitDataService backed interface for production usage.
 * See GtfsTransitDataServiceFacade for integration tests usage.
 */
@Component
public class TransitDataServiceFacadeImpl implements TransitDataServiceFacade {

    private TransitGraphDao _transitGraphDao;
    private ExtendedCalendarService _calendarService;
    private BlockCalendarService _blockCalendarService;
    private ScheduledBlockLocationService _blockLocationService;
    private BlockRunService _blockRunService;
    private BlockGeospatialService _blockGeospatialService;


    @Autowired
    public void setTransitGraphDao(TransitGraphDao transitGraphDao) {
        _transitGraphDao = transitGraphDao;
    }

    @Autowired
    public void setCalendarService(ExtendedCalendarService calendarService) {
        _calendarService = calendarService;
    }

    @Autowired
    public void setBlockCalendarService(BlockCalendarService blockCalendarService) {
        _blockCalendarService = blockCalendarService;
    }

    @Autowired
    public void setScheduledBlockLocationService(ScheduledBlockLocationService blockLocationService) {
        _blockLocationService = blockLocationService;
    }

    @Autowired
    public void setBlockRunSerivce(BlockRunService blockRunService) {

        _blockRunService = blockRunService;
    }

    @Autowired
    public void setBlockGeospatialService(BlockGeospatialService blockGeospatialService) {
        _blockGeospatialService = blockGeospatialService;
    }

    public List<TripEntry> getAllTrips() {
        return _transitGraphDao.getAllTrips();
    }

    public TripEntry getTripEntryForId(AgencyAndId id) {
        return _transitGraphDao.getTripEntryForId(id);
    }

    public Set<Date> getDatesForServiceIds(LocalizedServiceId serviceId) {
        return _calendarService.getDatesForServiceIds(new ServiceIdActivation(serviceId));
    }

    public boolean areServiceIdsActiveOnServiceDate(LocalizedServiceId serviceId, Date time) {
        return _calendarService.areServiceIdsActiveOnServiceDate(
                new ServiceIdActivation(serviceId), time);
    }

    public BlockInstance getBlockInstance(AgencyAndId blockId, long time) {
        return _blockCalendarService.getBlockInstance(blockId, time);
    }

    public ScheduledBlockLocation getScheduledBlockLocationFromScheduledTime(BlockConfigurationEntry config, int secondsIntoDay) {
        return _blockLocationService.getScheduledBlockLocationFromScheduledTime(config, secondsIntoDay);
    }

    public List<Integer> getBlockIds(int route, int blockRunNumber) {
        return _blockRunService.getBlockIds(route, blockRunNumber);
    }

    public ScheduledBlockLocation getBestScheduledBlockLocationForLocation(BlockInstance block,
                                                                           CoordinatePoint location,
                                                                           long timestamp,
                                                                           double start,
                                                                           double end) {
        return _blockGeospatialService.getBestScheduledBlockLocationForLocation(
                block, location, timestamp, start, end);
    }

}
