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

/**
 * Two trains have the same train_id, though one is historical.  Confirm only
 * one set of predictions flows through.
 * ./avl_record_to_json.sh '6: 2062' '2017-02-25 15:43:28' '2017-02-25 15:43:30'
 * ./avl_record_to_json.sh '6: 1069' '2017-02-25 15:43:28' '2017-02-25 15:43:30'
 */
public class LinkDec2016DuplicateTrains2Test extends AbstractFeedBuilderTest {

    public LinkDec2016DuplicateTrains2Test() throws Exception {
        super("LinkDec2016", "20170225", null, false);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_DuplicateTrain2.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        // two trips are present -- one is historical only
        assertEquals(1, feedMessage.getEntityCount());


        // first entity
        GtfsRealtime.FeedEntity e1 = feedMessage.getEntity(0);
        assertNotNull(e1);

        assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
        assertEquals("119:148:120", e1.getId());

        assertEquals("6", e1.getTripUpdate().getVehicle().getId());
    }
}
