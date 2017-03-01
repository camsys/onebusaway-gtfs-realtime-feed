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
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Train off route, beginning to travel backwards
 * ./avl_record_to_json.sh '4: 2070' '2017-01-27 13:16:52' '2017-01-27 13:16:54'
 *
 */
public class LinkDec2016OffRoute1Test extends AbstractFeedBuilderTest {

    public LinkDec2016OffRoute1Test() throws Exception {
        super("LinkDec2016", "20170127", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_OffRoute1.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        assertEquals(1, feedMessage.getEntityCount());

        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);
        assertEquals("found date of "
                        + new Date(e1.getTripUpdate().getTimestamp()*1000),
                new AvlParseServiceImpl().parseAvlTimeAsSeconds("2017-01-27T13:16:33.000-08:00"),
                e1.getTripUpdate().getTimestamp());

        // running quite a bit behind
        assertEquals(809, e1.getTripUpdate().getDelay());

        // first stop othello
        int count = e1.getTripUpdate().getStopTimeUpdateCount();
        GtfsRealtime.TripUpdate.StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdate(0);
        assertEquals("56159", e1st1.getStopId());


        // last stop ALS
        GtfsRealtime.TripUpdate.StopTimeUpdate e1stx = e1.getTripUpdate().getStopTimeUpdate(count-1);
        assertEquals("99914", e1stx.getStopId());

        // expecting a handful updates
        assertEquals(5, count);
    }
}
