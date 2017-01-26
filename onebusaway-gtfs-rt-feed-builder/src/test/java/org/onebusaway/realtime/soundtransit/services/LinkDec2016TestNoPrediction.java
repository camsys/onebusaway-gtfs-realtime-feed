package org.onebusaway.realtime.soundtransit.services;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.Test;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by sbrown on 1/26/17.
 */
public class LinkDec2016TestNoPrediction extends AbstractFeedBuilderTest {

    public LinkDec2016TestNoPrediction() throws Exception {
        super("LinkDec2016", "20170125",  1485394320000l);
    }

    @Test
    public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
        // this data is based off of KCM "12/2/2016  4:46 PM     17602061 google_daily_transit_2016_12_02.zip"
        LinkAVLData linkAVLData = avldp.parseAVLDataFromFile("src/test/resources/LinkAvlData_LinkDec2016_NoPrediction.txt");
        assertNotNull(linkAVLData);

        _feedService.setFrequencySchedule("false");
        GtfsRealtime.FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
        assertNotNull(feedMessage);
        // there is no prediction (no scheduleTime=null) so there is nothing to do!
        assertEquals(0, feedMessage.getEntityCount());

    }

}
