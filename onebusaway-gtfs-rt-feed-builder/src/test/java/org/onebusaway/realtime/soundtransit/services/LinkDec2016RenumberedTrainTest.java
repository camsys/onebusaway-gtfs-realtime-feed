/**
 * Copyright (C) 2017 Cambridge Systematics, Inc.
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
 * This train was renumbered, and hence has a missing actual in the first record.
 * However, this stop update occurs in the past and should not generate a prediction.
 * There are valid predictions in the remaining stop updates however.
 * Based on sample1.json provided 2017-01-31.
 */
public class LinkDec2016RenumberedTrainTest extends AbstractFeedBuilderTest {


    public LinkDec2016RenumberedTrainTest() throws Exception {
        super("LinkDec2016", "20170131", null, false);
    }



    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_RenumberedTrain.txt");
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

        assertTrue(e1.getTripUpdate().hasTrip());


        assertEquals("found date of " + new Date(e1.getTripUpdate().getTimestamp()*1000), new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-31T12:17:44.000-08:00"), e1.getTripUpdate().getTimestamp());
        GtfsRealtime.TripDescriptor td1 = e1.getTripUpdate().getTrip();

        assertEquals(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
        assertTrue(e1.getTripUpdate().hasVehicle());
        assertEquals("1", e1.getTripUpdate().getVehicle().getId());

        assertTrue(td1.hasTripId());
        Block b = ga.getBlockForRun(linkAVLData.getTrips().getTrips().get(0).getTripId().split(":")[0], ga.getServiceDate());
        assertEquals(4383160, b.getBlockSequence());
        assertEquals("31922401", td1.getTripId());

        // predicted=2017-01-31T12:19:55.000-08:00 - scheduled=2017-01-31T12:19:39.000-08:00 for future stop
        assertEquals(27, e1.getTripUpdate().getDelay());

        // we want multiple updates!
        assertEquals(13, e1.getTripUpdate().getStopTimeUpdateCount());
        GtfsRealtime.TripUpdate.StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
        assertTrue(e1st1.hasArrival());
        // make sure our first record is for University Street Station (455/SB1047T)
        assertEquals("455", e1st1.getStopId());
        // update needs to be in seconds, not millis!
        long e1st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-31T12:19:55.000-08:00");
        assertEquals(e1st1ArrivalTime, e1st1.getArrival().getTime());

        // make sure our last record is for Angle Lake
        int count = e1.getTripUpdate().getStopTimeUpdateCount();
        GtfsRealtime.TripUpdate.StopTimeUpdate e1stx = e1.getTripUpdate().getStopTimeUpdate(count-1);
        assertTrue("99913".equals(e1stx.getStopId()) || "99914".equals(e1stx.getStopId()));
    }
}
