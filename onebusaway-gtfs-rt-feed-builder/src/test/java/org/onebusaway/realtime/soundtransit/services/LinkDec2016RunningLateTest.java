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

import com.google.transit.realtime.GtfsRealtime;
import org.junit.Test;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.services.test.AbstractFeedBuilderTest;

import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verify behaviour of a train running significantly late.
 * Based on file sample3.json provided 2017-02-01
 */
public class LinkDec2016RunningLateTest extends AbstractFeedBuilderTest {

    public LinkDec2016RunningLateTest() throws Exception {
        super("LinkDec2016", "20170130", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_RunningLate.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(1, feedMessage.getEntityCount());

        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);
        // trip 31625833
        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());
        /* we are running WAY behind:
        * "Scheduled": "2017-02-01T07:44:00.000-08:00"
        * vs
        * "Estimated": "2017-02-01T08:50:27.000-08:00"
        * ~66 minutes at next stop
        */
        assertEquals(3912, e1.getTripUpdate().getDelay()); // 65 minutes instantaneous
        assertTrue(e1.getTripUpdate().hasTrip());

        assertEquals("found date of " + new Date(e1.getTripUpdate().getTimestamp()*1000),
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:47:44.000-08:00"),
                e1.getTripUpdate().getTimestamp());

        GtfsRealtime.TripDescriptor td1 = e1.getTripUpdate().getTrip();

        assertEquals(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
        assertTrue(e1.getTripUpdate().hasVehicle());
        assertEquals("1", e1.getTripUpdate().getVehicle().getId());

        assertTrue(td1.hasTripId());
        String run = linkAVLData.getTrips().getTrips().get(0).getTripId().split(":")[0];
        _log.info("run=" + run + " and serviceDate=" + ga.getServiceDate());
        Block b = ga.getBlockForRun(run, ga.getServiceDate());
        // block 40_4383174
        assertEquals(4383174, b.getBlockSequence());

        // ST_31922361
        assertEquals("31922361", td1.getTripId());
        // we are near end of trip, only three stops left to predict
        assertEquals(3, e1.getTripUpdate().getStopTimeUpdateCount());
        GtfsRealtime.TripUpdate.StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
        assertTrue(e1st1.hasArrival());
        // make sure our first record is for (Tukwila) Airport Station (99900/SB778T)
        assertEquals("99900", e1st1.getStopId());
        // update needs to be in seconds, not millis!
        long e1st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:50:27.000-08:00");
        assertEquals(e1st1ArrivalTime, e1st1.getArrival().getTime());


        GtfsRealtime.TripUpdate.StopTimeUpdate e1st2 = e1.getTripUpdate().getStopTimeUpdateList().get(1);
        assertTrue(e1st2.hasArrival());
        // second record is for airport
        assertEquals("99904", e1st2.getStopId());
        // update needs to be in seconds, not millis!
        long e1st2ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:53:27.000-08:00");
        assertEquals(e1st2ArrivalTime, e1st2.getArrival().getTime());


        // make sure our last record is for Angle Lake
        int count = e1.getTripUpdate().getStopTimeUpdateCount();
        GtfsRealtime.TripUpdate.StopTimeUpdate e1stx = e1.getTripUpdate().getStopTimeUpdate(count-1);
        assertEquals("99914", e1stx.getStopId());
        long e1st3ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:57:27.000-08:00");
        assertEquals(e1st3ArrivalTime, e1stx.getArrival().getTime());
    }
}
