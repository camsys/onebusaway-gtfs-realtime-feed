package org.onebusaway.realtime.soundtransit.services;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.Before;
import org.junit.Test;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.StopMapper;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.services.test.GtfsTransitDataServiceFacade;
import org.onebusaway.realtime.soundtransit.services.test.LinkGtfsAdaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.onebusaway.realtime.soundtransit.services.test.AVLDataParser.STOP_MAPPING_FILE;

/**
 * Basic test of TUFeedBuilder
 */
public class TUFeedBuilderScheduleServiceImplTest {

    private TUFeedBuilderScheduleServiceImpl _impl;
    private LinkStopServiceImpl _linkStopService;

    @Before
    public void setup() throws Exception {
        String bundleDir = "LinkDec2016";
        String serviceDateStr = "20170131";
        Long referenceTime = null;

        StopMapper stopMapper = new StopMapper();
        stopMapper.setLinkStopMappingFile(STOP_MAPPING_FILE);
        stopMapper.init();

        LinkGtfsAdaptor ga = new LinkGtfsAdaptor(bundleDir, serviceDateStr);
        GtfsTransitDataServiceFacade tds = new GtfsTransitDataServiceFacade(ga, serviceDateStr, referenceTime);

        AvlParseServiceImpl avlParseService = new AvlParseServiceImpl();
        _linkStopService = new LinkStopServiceImpl();
        _linkStopService.setStopMapper(stopMapper);

        TUFeedBuilderComponent comp = new TUFeedBuilderComponent();
        comp.setAvlParseService(avlParseService);
        comp.setLinkStopServiceImpl(_linkStopService);



        _impl = new TUFeedBuilderScheduleServiceImpl();
        _impl.setTUFeedBuilderComponent(comp);
        _impl.setLinkStopServiceImpl(_linkStopService);
        LinkTripServiceImpl ltsi = new LinkTripServiceImpl();
        _impl.setLinkTripServiceImpl(ltsi);
        ltsi.setTransitDataServiceFacade(tds);
        ltsi.setOverrideScheduleTime(false);
    }

    @Test
    public void testFindArrivalTimeUpdates() {

        List<StopUpdate> updates = new ArrayList<StopUpdate>();
        String tripId = "31922380";
        long lastUpdatedInSeconds = 0;
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> arrivalTimes
                = _impl.findArrivalTimeUpdates(updates, tripId, lastUpdatedInSeconds);
        assertNotNull(arrivalTimes);
        assertTrue(arrivalTimes.isEmpty());

        // Tukwilla
        addStopUpdate(updates, "SB778T",
                null,
                "2017-02-01T07:44:00.000-08:00",
                "2017-02-01T08:50:27.000-08:00");

        arrivalTimes
                = _impl.findArrivalTimeUpdates(updates, tripId, lastUpdatedInSeconds);
        assertEquals(1, arrivalTimes.size());
        assertEquals(_linkStopService.getGTFSStop("SB778T", null),  arrivalTimes.get(0).getStopId());


        // normal update
        // Airport
        addStopUpdate(updates, "SB889T",
                null,
                "2017-02-01T07:47:00.000-08:00",
                "2017-02-01T08:43:27.000-08:00");

        arrivalTimes
                = _impl.findArrivalTimeUpdates(updates, tripId, lastUpdatedInSeconds);
        assertEquals(2, arrivalTimes.size());
        assertEquals(_linkStopService.getGTFSStop("SB889T", null),  arrivalTimes.get(1).getStopId());


        // normal update
        addStopUpdate(updates, "ALS_PLAT",
                null,
                "2017-02-01T07:51:00.000-08:00",
                "2017-02-01T08:57:27.000-08:00");

        arrivalTimes
                = _impl.findArrivalTimeUpdates(updates, tripId, lastUpdatedInSeconds);
        assertEquals(3, arrivalTimes.size());
        assertEquals(_linkStopService.getGTFSStop("ALS_PLAT", null),  arrivalTimes.get(2).getStopId());


        // historical stop -- resets the previous stops
        addStopUpdate(updates, "NB1053T",
                "2017-02-01T08:15:27.000-08:00",
                "1899-12-30T00:00:00.000-08:00",
                "2017-02-01T08:15:27.000-08:00");

        arrivalTimes
                = _impl.findArrivalTimeUpdates(updates, tripId, lastUpdatedInSeconds);
        assertEquals(0, arrivalTimes.size());

    }


    private void addStopUpdate(List<StopUpdate> updates, String stopId,
                               String actual, String scheduled, String estimated) {
        StopUpdate su = new StopUpdate();
        su.setStopId(stopId);
        ArrivalTime at = new ArrivalTime();
        su.setArrivalTime(at);
        at.setActual(actual);
        at.setScheduled(scheduled);
        at.setEstimated(estimated);
        updates.add(su);
    }
}
