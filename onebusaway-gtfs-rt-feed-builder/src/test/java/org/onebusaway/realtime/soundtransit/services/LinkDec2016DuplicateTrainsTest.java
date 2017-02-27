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
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.services.test.AbstractFeedBuilderTest;

import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Two trains are assigned the same AVL TripId within short succession.
 * ./avl_record_to_json.sh '44: 2027' '2017-01-30 07:37:33' '2017-01-30 07:37:35'
 * ./avl_record_to_json.sh '44: 2027' '2017-01-30 07:41:03' '2017-01-30 07:41:05'
 *
 * The adaptor happily serves both of these even though they contradict.
 */
public class LinkDec2016DuplicateTrainsTest extends AbstractFeedBuilderTest {

    public LinkDec2016DuplicateTrainsTest() throws Exception {
        super("LinkDec2016", "20170130", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_DuplicateTrain.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(2, feedMessage.getEntityCount());


        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);
        assertNotNull(e1);

        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertEquals("126:151:161", e1.getId());
        // based on new logic train is 44 (TrainId) not "44: 2027" (TripId)
        assertEquals("44", e1.getTripUpdate().getVehicle().getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());


        /* we are running behind:
        * "Scheduled": "2017-02-01T07:23:00.000-08:00"
        * vs
        * "Estimated": "2017-02-01T07:38:51.000-08:00"
        * ~15 minutes at next stop
        */
        assertEquals(956, e1.getTripUpdate().getDelay()); // 15 minutes instantaneous
        assertTrue(e1.getTripUpdate().hasTrip());

        assertEquals("found date of " + new Date(e1.getTripUpdate().getTimestamp()*1000),
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:37:13.000-08:00"),
                e1.getTripUpdate().getTimestamp());


        // second entity
        GtfsRealtime.FeedEntity e2 = feedMessage.getEntity(1);

        assertEquals(linkAVLData.getTrips().getTrips().get(1).getVehicleId(), e2.getId());
        assertEquals("104:138:122", e2.getId());
        // based on new logic train is 44 (TrainId) not "44: 2027" (TripId)
        assertEquals("44", e2.getTripUpdate().getVehicle().getId());
        assertTrue(e2.hasTripUpdate());
        assertTrue(e2.getTripUpdate().hasDelay());

        /* we are running behind according to predictions
        * "Scheduled": "2017-01-30T07:25:00.000-08:00"
        * vs
        * "Estimated": "2017-01-30T07:40:33.000-08:00"
        * ~15 minutes at next stop
        * but the lat/lon has us ahead of schedule
        */
        assertEquals(-153, e2.getTripUpdate().getDelay()); // 2 minutes ahead
        assertTrue(e2.getTripUpdate().hasTrip());

        assertEquals("found date of " + new Date(e2.getTripUpdate().getTimestamp()*1000),
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:40:47.000-08:00"),
                e2.getTripUpdate().getTimestamp());

    }
}
