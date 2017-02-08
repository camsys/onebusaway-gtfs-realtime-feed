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

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.realtime.soundtransit.services.TransitDataServiceFacade;
import org.onebusaway.transit_data_federation.impl.transit_graph.BlockEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.BlockStopTimeEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.StopEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.StopTimeEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.TripEntryImpl;
import org.onebusaway.transit_data_federation.services.blocks.BlockGeospatialService;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockStopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockTripEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * GTFS-based implementation of TransitDataService methods.
 * Useful for integration tests, they simply need reference
 * the GTFS for the period, and not the full bundle.
 * These methods are minimally implemented so as to support
 * the integration tests.  Functionality should not be considered
 * complete.
 */
public class GtfsTransitDataServiceFacade
        implements TransitDataServiceFacade {

    protected static Logger _log = LoggerFactory.getLogger(GtfsTransitDataServiceFacade.class);

    @Autowired
    private BlockGeospatialService _blockGeospatialService;
    private LinkGtfsAdaptor _ga = null;
    private Long _referenceTime = null;
    private String _serviceDateStr = null;

    public GtfsTransitDataServiceFacade(LinkGtfsAdaptor ga, String serviceDateStr, Long aReferenceTime) {
        _ga =ga;
        _serviceDateStr = serviceDateStr;
        _referenceTime = aReferenceTime;
        setup();
    }

    public void setup() {
        /*
         * here we manually load spring to inject just the required TDS subclasses carefully
         * avoiding anything that requires the bundle.
         */
        _log.debug("loading spring...");
        ApplicationContext context = new ClassPathXmlApplicationContext(
                "gtfs-tds-data-sources.xml");

        context.getAutowireCapableBeanFactory().autowireBean(this);
        _log.debug("spring loaded!");
        _blockGeospatialService = (BlockGeospatialService) context.getBean("blockGeospatialService");

        NarrativeServiceTestImpl narrativeService = (NarrativeServiceTestImpl) context.getBean("narrativeService");
        narrativeService.setFacade(this);

    }

    private String mapDirection(String gtfsDirection) {
        return gtfsDirection;
    }

    public long getStartOfReferenceDay() {
        Calendar c = Calendar.getInstance();
        if (_referenceTime != null) {
            c.setTimeInMillis(_referenceTime);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            Date d = null;
            try {
                d = sdf.parse(_serviceDateStr);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            c.setTime(d);
        }
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public List<ShapePoint> getShapePointsForId(AgencyAndId id) {
        return _ga.getShapePointsForId(id);
    }
    @Override
    public List<TripEntry> getAllTrips() {
        throw new UnsupportedOperationException("getAllTrips not implemented");
    }

    @Override
    public TripEntryTestImpl getTripEntryForId(AgencyAndId id) {
        TripEntryTestImpl i = new TripEntryTestImpl();
        Trip trip = _ga.getTripById(id.getId());
        i.setId(trip.getId());
        i.setShapeId(trip.getShapeId());
        i.setDirectionId(mapDirection(trip.getDirectionId()));
        LocalizedServiceId sid = new LocalizedServiceId(trip.getServiceId(), TimeZone.getDefault());

        BlockEntryImpl bei = new BlockEntryImpl();
        bei.setId(new AgencyAndId(id.getAgencyId(), trip.getBlockId()));
        i.setBlock(bei);
        double totalTripDistance = 0.0;


        for (StopTime stopTime : _ga.getStopTimesForTripId(trip.getId().getId())) {
            StopTimeEntryImpl se = new StopTimeEntryImpl();
            se.setArrivalTime(stopTime.getArrivalTime());
            se.setDepartureTime(stopTime.getDepartureTime());
            se.setTrip(toTripEntryImpl(i));
            se.setId(stopTime.getId());
            se.setStop(toStopEntry(stopTime.getStop()));
            se.setShapeDistTraveled(toMeters(stopTime.getShapeDistTraveled()));
            i.getStopTimes().add(se);
            // KCM GTFS is in ft, TDS is in meters
            totalTripDistance+=toMeters(stopTime.getShapeDistTraveled());
        }
        i.setTotalTripDistance(totalTripDistance);

        assert(i.getStopTimes().get(0).getTrip() != null);

        return i;
    }

    private double toMeters(double ft) {
        return ft * 0.3048;
    }

    @Override
    public Set<Date> getDatesForServiceIds(LocalizedServiceId serviceId) {
        _log.error("getDatesForServiceIds called");
        throw new UnsupportedOperationException("getDatesForServiceIds not implemented");
    }

    @Override
    public boolean areServiceIdsActiveOnServiceDate(LocalizedServiceId serviceId, Date time) {
        _log.error("areServiceIdsActiveOnServiceDate called");
        throw new UnsupportedOperationException("areServiceIdsActiveOnServiceDate not implemented");
    }

    @Override
    public BlockInstance getBlockInstance(AgencyAndId blockId, long serviceDateInMillis) {

        if (!isActive(blockId, serviceDateInMillis)) {
            return null;
        }
        Trip trip = _ga.getFirstTrip(blockId.getId());
        TripEntryTestImpl te = getTripEntryForId(trip.getId());
        BlockConfigurationEntryImpl bce = new BlockConfigurationEntryImpl();

        BlockTripEntryTestImpl bte = new BlockTripEntryTestImpl();

        bte.setTrip(te);
        int blockSequence = 0;
        while (bte != null) {
            bce.getTrips().add(bte);
            bce.getStopTimes().addAll(toStopTimes(bte, getStopTimes(bte), blockSequence));
            _log.debug("looking for next trip after " + bte.getTrip().getId());
            bte = findNextTrip(bte.getTrip().getId());
            blockSequence++;
            if (bte != null && bte.getTrip() != null) {
                _log.debug("trip=" + te.getId()
                        + " blockSequence=" + blockSequence
                        + " nextTrip=" + bte.getTrip().getId());
            }
        }
        _log.debug("block " + blockId + " has trips=" + bce.getTrips());
        BlockInstance bi = new BlockInstance(bce, serviceDateInMillis);

        return bi;
    }
    public List<BlockStopTimeEntry> toStopTimes(BlockTripEntryTestImpl bte, List<StopTime> stopTimes, int blockSequence) {
        List<BlockStopTimeEntry> entries = new ArrayList<BlockStopTimeEntry>();

        int stCount = 0;
        for (StopTime st : stopTimes) {
            StopTimeEntryImpl stei = new StopTimeEntryImpl();
            stei.setId(st.getId());
            stei.setArrivalTime(st.getArrivalTime());
            stei.setDepartureTime(st.getDepartureTime());
            stei.setStop(toStopEntry(st.getStop()));

            Trip trip = st.getTrip();
            TripEntryTestImpl te = getTripEntryForId(trip.getId());
            stei.setTrip(toTripEntryImpl(te));

            //StopTimeEntry stopTime, int blockSequence,
            //BlockTripEntry trip, boolean hasNextStop
            BlockStopTimeEntry e = new BlockStopTimeEntryImpl(stei,
                    blockSequence,
                    bte,
                    (stCount != stopTimes.size()-1) );
            stCount++;
            entries.add(e);
        }

        return entries;
    }

    private StopEntryImpl toStopEntry(Stop s) {
        StopEntryImpl i = new StopEntryImpl(s.getId(), s.getLat(), s.getLon());
        return i;
    }

    private TripEntryImpl toTripEntryImpl(TripEntryTestImpl t) {
        TripEntryImpl i = new TripEntryImpl();
        i.setId(t.getId());
        i.setShapeId(t.getShapeId());
        i.setDirectionId(t.getDirectionId());
        i.setServiceId(t.getServiceId());
        i.setStopTimes(t.getStopTimes());
        return i;
    }
    private boolean isActive(AgencyAndId blockId, long serviceDateInMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(serviceDateInMillis);
        Trip trip = _ga.getFirstTrip(blockId.getId());
        AgencyAndId serviceId = trip.getServiceId();
        ServiceCalendar serviceCalendar = _ga.getCalendarByServiceId(serviceId.getId());

        if (serviceCalendar.getStartDate().getAsDate().getTime() > serviceDateInMillis) {
            _log.debug("calendar " + serviceCalendar + " rejected as startDate beyond serviceDate "
                + serviceCalendar.getStartDate().getAsDate() + " >! " + new Date(serviceDateInMillis));
            return false;
        }
        if (serviceCalendar.getEndDate().getAsDate().getTime() < serviceDateInMillis) {
            _log.debug("calendar " + serviceCalendar + " rejected as endDate before serviceDate "
                    + serviceCalendar.getEndDate().getAsDate() + " <! " + new Date(serviceDateInMillis));
            return false;
        }

        boolean found = false;
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                if (serviceCalendar.getMonday() > 0) found = true;
                break;
            case Calendar.TUESDAY:
                if (serviceCalendar.getTuesday() > 0) found = true;
                break;
            case Calendar.WEDNESDAY:
                if (serviceCalendar.getWednesday() > 0) found = true;
                break;
            case Calendar.THURSDAY:
                if (serviceCalendar.getThursday() > 0) found = true;
                break;
            case Calendar.FRIDAY:
                if (serviceCalendar.getFriday() > 0) found = true;
                break;
            case Calendar.SATURDAY:
                if (serviceCalendar.getSaturday() > 0) found = true;
                break;
            case Calendar.SUNDAY:
                if (serviceCalendar.getSunday() > 0) found = true;
                break;
            default:
                throw new IllegalStateException("unknown day=" + c.get(Calendar.DAY_OF_WEEK));
        }
        if (found == true) {
            _log.debug("calendar match=" + serviceCalendar);
            found = true;
            return found;
        }

        // on fall through check to see if service exception applies
        // this logic isn't exactly correct but should suffice for integration tests
        ServiceCalendarDate exception = _ga.getCalendarException(serviceCalendar.getServiceId().getId(), serviceDateInMillis);
        if (exception != null) {
            _log.debug("exception match=" + exception);
            found = true;
            return found;
        }
        _log.debug("calendar " + serviceCalendar + " did not match day=" + c.get(Calendar.DAY_OF_WEEK));
        return false;
    }

    private Map<AgencyAndId, TripEntryTestImpl> _nextTripCache = new HashMap<AgencyAndId, TripEntryTestImpl>();
    private Map<AgencyAndId, BlockTripEntryTestImpl> _nextBlockTripCache = new HashMap<AgencyAndId, BlockTripEntryTestImpl>();

    private BlockTripEntryTestImpl findNextTrip(Trip trip) {
        return findNextTrip(trip.getId());
    }

    private BlockTripEntryTestImpl findNextTrip(AgencyAndId tripId) {
        if (_nextBlockTripCache.containsKey(tripId)) {
            _log.debug("cache hit:  findNextTrip(" + tripId + ")=" + _nextBlockTripCache.get(tripId));
            return _nextBlockTripCache.get(tripId);
        }

        Trip trip = _ga.getTripById(tripId.getId());
        String blockId = trip.getBlockId();
        assert(blockId != null);
        List<StopTime> stopTimesForTripId = _ga.getStopTimesForTripId(trip.getId().getId());
        long lastArrivalTime = stopTimesForTripId.get(stopTimesForTripId.size() - 1).getArrivalTime();

        long smallestArrivalTime = Long.MAX_VALUE;
        _log.debug("starting with lastArrivalTime=" + lastArrivalTime
                + " and smallestArrivalTime=" + smallestArrivalTime);

        Trip nextTrip = null;
        for (Trip aTrip : _ga.getAllTrips()) {
            if (blockId.equals(aTrip.getBlockId())) {
                int aStopTime = _ga.getStopTimesForTripId(aTrip.getId().getId()).get(0).getArrivalTime();
                _log.debug("aStopTime=" + aStopTime + " ?> " + lastArrivalTime + " smallest=" + smallestArrivalTime);
                if (aStopTime > lastArrivalTime && aStopTime < smallestArrivalTime) {
                    smallestArrivalTime = aStopTime;
                    nextTrip = aTrip;
                }
            }
        }
        if (nextTrip == null) {
            _log.debug("ran off end for trip=" + trip.getId() + " on block " + trip.getBlockId()
            + " with lastTime=" + lastArrivalTime + " and smallest=" + smallestArrivalTime);
            return null;
        }

        _log.debug("trip " + trip.getId() + " has nextTrip of " + nextTrip
        + " with lastArrivalTime=" + lastArrivalTime + " and smallest=" + smallestArrivalTime);

        assert(tripId != nextTrip.getId());

        BlockTripEntryTestImpl bte = loadBlockTripEntry(nextTrip.getId());
        _nextTripCache.put(tripId, (TripEntryTestImpl)bte.getTrip());
        _nextBlockTripCache.put(tripId, bte);

        return bte;
    }

    private BlockTripEntryTestImpl loadBlockTripEntry(AgencyAndId id) {
        if (_nextBlockTripCache.containsKey(id)) return _nextBlockTripCache.get(id);

        Trip trip = _ga.getTripById(id.getId());
        BlockTripEntryTestImpl bte = new BlockTripEntryTestImpl();
        TripEntryTestImpl te = new TripEntryTestImpl();
        te.setId(trip.getId());
        te.setDirectionId(mapDirection(trip.getDirectionId()));
        bte.setTrip(te);

        for (StopTime stopTime : getStopTimes(bte)) {
            StopTimeEntryImpl i = new StopTimeEntryImpl();
            i.setArrivalTime(stopTime.getArrivalTime());
            i.setDepartureTime(stopTime.getDepartureTime());
            te.getStopTimes().add(i);
        }

        return bte;
    }

    @Override
    public ScheduledBlockLocation getScheduledBlockLocationFromScheduledTime(BlockConfigurationEntry config, int secondsIntoDay) {
        ScheduledBlockLocation sbl = new ScheduledBlockLocation();
        boolean found = false;
        if (config == null)
            throw new IllegalStateException("missing config");
        if (config.getTrips() == null || config.getTrips().isEmpty()) {
            throw new IllegalStateException("empty trips for config");
        }
        int firstTrip = 0;
        int lastTrip = 0;
        int bestTripStart = 0;
        int bestTripEnd = 0;
        for (BlockTripEntry bte : config.getTrips()) {
            List<StopTime> stopTimesForTripId = _ga.getStopTimesForTripId(bte.getTrip().getId().getId());
            int tripStart = stopTimesForTripId.get(0).getArrivalTime();
            int tripEnd = stopTimesForTripId.get(stopTimesForTripId.size()-1).getArrivalTime();
            if (firstTrip == 0) {
                firstTrip = tripStart;
            }
            if (lastTrip == 0) {
                lastTrip = firstTrip;
            }
            _log.debug("lastTrip=" + lastTrip + ", " + tripStart + " <? " + secondsIntoDay + " <? " + tripEnd);
            // look at end of last trip so we don't miss layover gaps
            if (secondsIntoDay >= lastTrip && secondsIntoDay <= tripEnd) {
                sbl.setActiveTrip(bte);
                found = true;
                bestTripStart = tripStart;
                bestTripEnd = tripEnd;
            }
            if (tripEnd > lastTrip)
                lastTrip = tripEnd;

        }

        if (!found) {
            _log.info("could not find location with secondsIntoDay=" + secondsIntoDay
                    + " (" + new Date(getStartOfReferenceDay() + secondsIntoDay*1000) + ")"
            + " with firstTrip=" + new Date(getStartOfReferenceDay() + firstTrip*1000)
                    + " and lastTrip=" + new Date(getStartOfReferenceDay() + lastTrip*1000));
            return null;
        } else {
            _log.debug(" best match trip=" + sbl.getActiveTrip().getTrip().getId()
                    + " " + bestTripStart + " " + secondsIntoDay + " " + bestTripEnd);
        }
        return sbl;
    }

    @Override
    public List<Integer> getBlockIds(int route, int blockRunNumber) {
        List<Integer> results = new ArrayList<Integer>();
        for (Block block : _ga.getAllBlocks()) {
            if (route == block.getBlockRoute() && blockRunNumber == block.getBlockRun()) {
                results.add(block.getBlockSequence());
            }
        }
        if (results.isEmpty()) {
            _log.error("NO MATCH FOR BLOCKIDS(" + route + ", " + blockRunNumber + ")");
        }
        return results;
    }


    public ScheduledBlockLocation getBestScheduledBlockLocationForLocation(BlockInstance block, CoordinatePoint location, long timestamp, double start, double end) {
        // this is just a logged pass-through to the spring-loaded TDS (but avoiding bundle dependencies)
        _log.debug("bgs=" + _blockGeospatialService);
        _log.debug("base time=" + getStartOfReferenceDay() + "(" + new Date(getStartOfReferenceDay()) + ")");
        _log.debug("location=" + location + " and timestamp=" + new Date(timestamp*1000));
        return _getBestScheduledBlockLocationForLocation(block, location, timestamp, start, end);
    }

    private ScheduledBlockLocation _getBestScheduledBlockLocationForLocation(
            BlockInstance blockInstance, CoordinatePoint location, long timestamp,
            double blockDistanceFrom, double blockDistanceTo) {
        // delegate to spring's bean
        return _blockGeospatialService.getBestScheduledBlockLocationForLocation(
                blockInstance, location, timestamp, blockDistanceFrom, blockDistanceTo);
    }


    private List<StopTime> getStopTimes(BlockTripEntryTestImpl bte) {
        List<StopTime> stopTimes = _ga.getStopTimesForTripId(bte.getTrip().getId().getId());
        return stopTimes;
    }
}
