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
import org.onebusaway.realtime.soundtransit.services.test.AbstractFeedBuilderTest;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Southbound Train offschedule and proceeding northbound on northbound track as
 * tracks are closed in the middle.
 * Provided as sample4.json on 2017-02-01.
 */
public class LinkDec2016WrongTrackTest extends AbstractFeedBuilderTest {

    public LinkDec2016WrongTrackTest() throws Exception {
        super("LinkDec2016", "20170131", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_WrongTrack.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(1, feedMessage.getEntityCount());


        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);

        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertEquals("142:112", e1.getId());
        // based on new logic train is 1 (TrainId) not "44: 2027" (TripId)
        assertEquals("1", e1.getTripUpdate().getVehicle().getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());

        /*
        * we are running WAY behind:
        * "Scheduled": "2017-02-01T07:44:00.000-08:00"
        * vs
        * "Estimated": "2017-02-01T08:50:27.000-08:00"
         */
        assertEquals(4134, e1.getTripUpdate().getDelay()); // 65 minutes instantaneous
        assertTrue(e1.getTripUpdate().hasTrip());

        // expecting 3 valid updates, the remaining ones have invalid schedules
        assertEquals(3, e1.getTripUpdate().getStopTimeUpdateCount());

        // last update date
        assertEquals(
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:18:11.000-08:00"),
                e1.getTripUpdate().getTimestamp());

        // update 1
        // tukwilla stop
        assertEquals("99900", e1.getTripUpdate().getStopTimeUpdate(0).getStopId());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:50:27.000-08:00"),
                e1.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime());

        // update 2
        // airport stop
        // note:  this prediction is out of order, but we happily pass it along
        assertEquals("99904", e1.getTripUpdate().getStopTimeUpdate(1).getStopId());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:43:27.000-08:00"),
                e1.getTripUpdate().getStopTimeUpdateList().get(1).getArrival().getTime());

        // update 3
        // angle lake stop
        assertEquals("99914", e1.getTripUpdate().getStopTimeUpdate(2).getStopId());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-02-01T08:57:27.000-08:00"),
                e1.getTripUpdate().getStopTimeUpdateList().get(2).getArrival().getTime());

    }

}
