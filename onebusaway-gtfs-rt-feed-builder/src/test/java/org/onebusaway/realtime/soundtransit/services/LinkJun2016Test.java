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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Benchmark of real data and expected behaviour from 2016-06-23.  Very useful
 * for testing changes against adaptor.
 */
public class LinkJun2016Test extends AbstractFeedBuilderTest {

    public LinkJun2016Test() throws Exception {
        super("LinkJun2016", "20160623", null, true);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkJun2016_All.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(14, feedMessage.getEntityCount());

        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);
        // trip 31625833
        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertTrue(e1.hasTripUpdate());
        assertTrue(e1.getTripUpdate().hasDelay());

        assertTrue(e1.getTripUpdate().hasTrip());
        assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:09:28.467-07:00"), e1.getTripUpdate().getTimestamp());
        GtfsRealtime.TripDescriptor td1 = e1.getTripUpdate().getTrip();

        assertEquals(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
        assertTrue(e1.getTripUpdate().hasVehicle());
        assertEquals("1: 6", e1.getTripUpdate().getVehicle().getId());
        assertEquals(111, e1.getTripUpdate().getDelay());

        assertTrue(td1.hasTripId());
        Block b = ga.getBlockForRun(linkAVLData.getTrips().getTrips().get(0).getTripId().split(":")[0], ga.getServiceDate());
        assertEquals(4237416, b.getBlockSequence());
        // tripId "1: 6" has run of 1 and via block.txt has block of 4237416
        assertEquals("31625834", td1.getTripId());
        // we want multiple updates!
        assertEquals(3, e1.getTripUpdate().getStopTimeUpdateCount());
        GtfsRealtime.TripUpdate.StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
        assertTrue(e1st1.hasArrival());
        // update needs to be in seconds, not millis!
        long e1st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:11:00.000-07:00");
        assertEquals(e1st1ArrivalTime, e1st1.getArrival().getTime());


        // second entity
        GtfsRealtime.FeedEntity e2 = feedMessage.getEntity(1);
        GtfsRealtime.TripDescriptor td2 = e2.getTripUpdate().getTrip();
        assertTrue(td2.hasTripId());
        assertEquals("31625956", td2.getTripId());
        assertEquals("10: 361", e2.getTripUpdate().getVehicle().getId());
        // third entity
        GtfsRealtime.FeedEntity e3 = feedMessage.getEntity(2);
        GtfsRealtime.TripDescriptor td3 = e3.getTripUpdate().getTrip();
        assertTrue(td3.hasTripId());
        assertEquals("11: 390", e3.getTripUpdate().getVehicle().getId());
        assertEquals("31625843", td3.getTripId());
        assertTrue(e3.hasTripUpdate());
        assertTrue(e3.getTripUpdate().hasDelay());
   /*
   "StopId": "SEA_PLAT",
   "StationName": "Airport Station",
   "Frequency": "0",
   "ArrivalTime": {
     "Actual": null,
     "Scheduled": "2016-06-23T08:11:00.000-07:00",
     "Estimated": "2016-06-23T08:10:20.000-07:00"
   }
   */
        GtfsRealtime.TripUpdate.StopTimeUpdate e3st1 = e3.getTripUpdate().getStopTimeUpdateList().get(0);
        assertTrue(e3st1.hasArrival());
        // update needs to be in seconds, not millis!
        long e3st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:10:20.000-07:00");
        assertEquals(e3st1ArrivalTime, e3st1.getArrival().getTime());

        /*
        * this is interesting:
        * SCADA reports at last stop:
        * 2016-06-23T08:10:20.000-07:00 - 2016-06-23T08:11:00.000-07:00 = -40 (delay)
        * but OBA reports (instantaneous, not last stop):
        * Thu Jun 23 08:09:28 PDT 2016 - Thu Jun 23 08:08:57 PDT 2016 = 31 (delay)
        * So OBA claims the train has lost some time relative to schedule.  Perhaps it
        * was held at Tuwkila station.
        */
        assertEquals(td3.getTripId() + " has invalid delay", 31, e3.getTripUpdate().getDelay());


        // trip "11: 390" is running early, verify the estimated and not the scheduled time comes through
        GtfsRealtime.FeedEntity e4 = findByVehicleId(feedMessage, "12: 423");
        assertNotNull(e4);
        long e4st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:13:42.000-07:00");
        assertEquals(e4st1ArrivalTime, e4.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime());

        // trip "15: 459" running very near schedule
        GtfsRealtime.FeedEntity e5 = findByVehicleId(feedMessage, "15: 459");
        assertNotNull(e5);

        assertEquals(47, e5.getTripUpdate().getDelay());

        // validate the remaining updates and verify the estimated times are greater (in the future)
        // as compared to the lastUpdatedDate
        for (GtfsRealtime.FeedEntity e : feedMessage.getEntityList()) {
            GtfsRealtime.TripUpdate.StopTimeUpdate stu11 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
            assertTrue(stu11.getArrival().getTime() > e.getTripUpdate().getTimestamp());
        }
    }

}
