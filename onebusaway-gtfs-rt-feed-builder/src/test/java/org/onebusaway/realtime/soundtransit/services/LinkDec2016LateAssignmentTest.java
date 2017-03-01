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
 * Train came online with trip after it was already in progress
 * ./avl_record_to_json.sh '1: 1041' '2017-01-30 09:14:50' '2017-01-30 09:15:00'
 */
public class LinkDec2016LateAssignmentTest extends AbstractFeedBuilderTest {

    public LinkDec2016LateAssignmentTest() throws Exception {
        super("LinkDec2016", "20170130", null, false);
    }

    @Test
    @Override
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {

        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_LateAssignment.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(2, feedMessage.getEntityCount());


        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);

        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());

        // train roughly on schedule
        assertEquals(-40, e1.getTripUpdate().getDelay());

        assertTrue(e1.getTripUpdate().hasTrip());


        assertEquals("found date of "
                + new Date(e1.getTripUpdate().getTimestamp()*1000),
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T09:14:19.000-08:00"),
                e1.getTripUpdate().getTimestamp());
        GtfsRealtime.TripDescriptor td1 = e1.getTripUpdate().getTrip();

        assertEquals(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
        assertTrue(e1.getTripUpdate().hasVehicle());
        assertEquals("1", e1.getTripUpdate().getVehicle().getId());

        assertTrue(td1.hasTripId());
        Block b = ga.getBlockForRun(linkAVLData.getTrips().getTrips().get(0).getTripId().split(":")[0], ga.getServiceDate());
        assertEquals(4383160, b.getBlockSequence());
        assertEquals("31922233", td1.getTripId());

        // first stop should be ranier -- train left tukwilla
        GtfsRealtime.TripUpdate.StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
        assertTrue(e1st1.hasArrival());
        assertEquals("55578", e1st1.getStopId());

        int size = e1.getTripUpdate().getStopTimeUpdateCount();
        // expecting 13 stops
        assertEquals(13, size);
        GtfsRealtime.TripUpdate.StopTimeUpdate e1stx = e1.getTripUpdate().getStopTimeUpdateList().get(size-1);
        assertTrue(e1stx.hasArrival());
        // last stop UWS
        assertEquals("99605", e1stx.getStopId());

    }
}
