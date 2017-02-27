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
 * Mismatched predictions.  Lat/Lon has train leaving Tukwilla, but predictions put it
 * at Mt. Baker AND Tukwilla.  Throw away Mt. Baker predictions to provide a corrected
 * feed.
 */
public class LinkDec2016MissingStopsTest extends AbstractFeedBuilderTest {
    public LinkDec2016MissingStopsTest() throws Exception {
        super("LinkDec2016", "20170130", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_MissingStops.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(1, feedMessage.getEntityCount());


        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);

        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertEquals("127:160", e1.getId());

        assertEquals("6", e1.getTripUpdate().getVehicle().getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());

        /*
        * we are running behind according to schedule for Mt.Baker
        * "Scheduled": "2017-01-30T07:24:00.000-08:00"
        * vs
        * "Estimated": "2017-01-30T07:41:36.000-08:00"
        * but ahead according to lat/lon
        * lat lon puts it just past Tukwilla with an scheduled of 7:50 and last update 7:48
        * "Scheduled": "2017-01-30T07:50:00.000-08:00"
        * vs
        * "Estimated": "2017-01-30T07:48:03.000-08:00"
         */
        assertEquals(-114, e1.getTripUpdate().getDelay()); // 1 minutes instantaneous
        assertTrue(e1.getTripUpdate().hasTrip());

        // expecting 2 valid updates, the remaining ones have invalid schedules
        // we discard the Mt. Baker prediction!
        assertEquals(2, e1.getTripUpdate().getStopTimeUpdateCount());

        // last update date
        assertEquals(
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:48:00.000-08:00"),
                e1.getTripUpdate().getTimestamp());

//        // update 1
//        // mt baker stop
//        assertEquals("55949", e1.getTripUpdate().getStopTimeUpdate(0).getStopId());
//        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:48:36.000-08:00"),
//                e1.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime());
//
//        // update 2
//        // columbia city stop
//        //
//        assertEquals("56039", e1.getTripUpdate().getStopTimeUpdate(1).getStopId());
//        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:51:36.000-08:00"),
//                e1.getTripUpdate().getStopTimeUpdateList().get(1).getArrival().getTime());
//
//        // update 3
//        // othello stop
//        assertEquals("56159", e1.getTripUpdate().getStopTimeUpdate(2).getStopId());
//        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:55:36.000-08:00"),
//                e1.getTripUpdate().getStopTimeUpdateList().get(2).getArrival().getTime());
//
//        // update 4
//        // rainier stop
//        assertEquals("56173", e1.getTripUpdate().getStopTimeUpdate(3).getStopId());
//        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:58:36.000-08:00"),
//                e1.getTripUpdate().getStopTimeUpdateList().get(3).getArrival().getTime());
//
//
//        // update 5
//        // tukwila stop
//        assertEquals("99900", e1.getTripUpdate().getStopTimeUpdate(4).getStopId());
//        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:48:03.000-08:00"),
//                e1.getTripUpdate().getStopTimeUpdateList().get(4).getArrival().getTime());


        // update 1
        // tukwila stop
        assertEquals("99904", e1.getTripUpdate().getStopTimeUpdate(0).getStopId());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:53:00.000-08:00"),
                e1.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime());

        // update 2
        // angle lake stop
        assertEquals("99914", e1.getTripUpdate().getStopTimeUpdate(1).getStopId());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-30T07:57:00.000-08:00"),
                e1.getTripUpdate().getStopTimeUpdateList().get(1).getArrival().getTime());

    }



}
