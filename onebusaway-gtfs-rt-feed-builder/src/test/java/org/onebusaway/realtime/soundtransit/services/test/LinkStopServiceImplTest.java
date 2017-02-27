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
package org.onebusaway.realtime.soundtransit.services.test;

import org.junit.Test;
import org.onebusaway.realtime.soundtransit.model.ArrivalTime;
import org.onebusaway.realtime.soundtransit.model.StopMapper;
import org.onebusaway.realtime.soundtransit.model.StopUpdate;
import org.onebusaway.realtime.soundtransit.model.StopUpdatesList;
import org.onebusaway.realtime.soundtransit.services.LinkStopServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Basic test of LinkStopService.
 */
public class LinkStopServiceImplTest {

    private static Logger _log = LoggerFactory.getLogger(LinkStopServiceImplTest.class);

    @Test
    public void testFindNextStopOnTrip() {
        LinkStopServiceImpl impl = new LinkStopServiceImpl();
        AVLDataParser avldp = new AVLDataParser(null);
        StopMapper stopMapper = new StopMapper();
        impl.setStopMapper(stopMapper);
        impl.setStopMapping(avldp.buildStopMapping(AVLDataParser.STOP_MAPPING_FILE));
        StopUpdatesList l = new StopUpdatesList();
        assertNull(impl.findNextStopOnTrip(l));
        List<StopUpdate> updates = new ArrayList<StopUpdate>();
        l.setUpdates(updates);
        assertNull(impl.findNextStopOnTrip(l));
        addStopUpdate(l.getUpdates(),
                "SB1087T",
                "International District Chinatown Station",
                "2017-01-30T07:39:35.000-08:00",
                "2017-01-30T07:22:00.000-08:00",
                "2017-01-30T07:40:05.000-08:00");


        StopUpdate nextStopOnTrip = impl.findNextStopOnTrip(l);
        _log.info("found stop=" + nextStopOnTrip);
        assertEquals("SB1087T", nextStopOnTrip.getStopId());
        addStopUpdate(l.getUpdates(),
                "SB113T",
                "Stadium Station",
                null,
                "2017-01-30T07:24:00.000-08:00",
                "2017-01-30T07:41:36.000-08:00"
                );

        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("SB113T", nextStopOnTrip.getStopId());

        addStopUpdate(l.getUpdates(),
                "SB148T",
                "SODO Station",
                null,
                "2017-01-30T07:26:00.000-08:00",
                "2017-01-30T07:43:36.000-08:00"
        );

        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("SB148T", nextStopOnTrip.getStopId());

        addStopUpdate(l.getUpdates(),
                "SB210T",
                "Beacon Hill Station",
                null,
                "2017-01-30T07:29:00.000-08:00",
                "2017-01-30T07:46:36.000-08:00"
        );

        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("SB210T", nextStopOnTrip.getStopId());

        // here we throw a wrench into the works!
        // we have a competing/contradictory update
        addStopUpdate(l.getUpdates(),
                "SB778T",
                "Tukwila International Boulevard Station",
                "2017-01-30T07:48:00.000-08:00",
                "2017-01-30T07:50:00.000-08:00",
                "2017-01-30T07:48:03.000-08:00"
        );

        // with the update above, we return the last stop on trip
        assertNotNull(impl.findNextStopOnTrip(l));
        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("SB778T", nextStopOnTrip.getStopId());

        addStopUpdate(l.getUpdates(),
                "SB889T",
                "Airport Station",
                null,
                "2017-01-30T07:53:00.000-08:00",
                "2017-01-30T07:53:00.000-08:00"
        );

        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("SB889T", nextStopOnTrip.getStopId());

        addStopUpdate(l.getUpdates(),
                "ALS_PLAT",
                "Angle Lake Station",
                null,
                "2017-01-30T07:57:00.000-08:00",
                "2017-01-30T07:57:00.000-08:00"
        );

        nextStopOnTrip = impl.findNextStopOnTrip(l);
        assertEquals("ALS_PLAT", nextStopOnTrip.getStopId());
    }

    private void addStopUpdate(List<StopUpdate> updates,
                               String stopId,
                               String stopName,
                               String actual,
                               String scheduled,
                               String estimated) {
        StopUpdate s = new StopUpdate();
        s.setStopId(stopId);
        s.setStationName(stopName);
        ArrivalTime at = new ArrivalTime();
        at.setActual(actual);
        at.setScheduled(scheduled);
        at.setEstimated(estimated);
        s.setArrivalTime(at);
        updates.add(s);

    }

}
