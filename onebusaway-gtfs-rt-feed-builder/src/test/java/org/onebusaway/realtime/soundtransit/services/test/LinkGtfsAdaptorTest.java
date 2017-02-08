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

import org.junit.Before;
import org.junit.Test;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LinkGtfsAdaptorTest {
  
  private LinkGtfsAdaptor l;
  @Before
  public void setup() throws Exception {
    l = new LinkGtfsAdaptor("LinkJun2016", "20160623");
  }
  @Test
  public void testIsActiveServiceId() throws Exception {
    Trip trip = l.getTripById("31625826"); // weekday service
    assertNotNull(trip);
    ServiceDate serviceDate = ServiceDate.parseString("20160623");
    assertTrue(l.isActiveServiceId(trip, serviceDate));
    
    // before calendar start
    serviceDate = ServiceDate.parseString("20160619"); 
    assertFalse(l.isActiveServiceId(trip, serviceDate));
    // after calendar end
    serviceDate = ServiceDate.parseString("20160910");
    assertFalse(l.isActiveServiceId(trip, serviceDate));
    // sat of active interval
    serviceDate = ServiceDate.parseString("20160625");
    assertFalse(l.isActiveServiceId(trip, serviceDate));
    // sun of active interval
    serviceDate = ServiceDate.parseString("20160626");
    assertFalse(l.isActiveServiceId(trip, serviceDate));
    
    trip = l.getTripById("30943814"); // sunday service
    assertNotNull(trip);
    serviceDate = ServiceDate.parseString("20160623");
    assertFalse(l.isActiveServiceId(trip, serviceDate));
    
    
    trip = l.getTripById("31625507"); // saturday service
    assertNotNull(trip);
    serviceDate = ServiceDate.parseString("20160623");
    assertFalse(l.isActiveServiceId(trip, serviceDate));

    trip = l.getTripById("31625843"); 
    assertNotNull(trip);
    serviceDate = ServiceDate.parseString("20160623");
    assertTrue(l.isActiveServiceId(trip, serviceDate));
    
  }

  
  @Test
  public void testFilterActiveBlocks() throws Exception {
    ServiceDate serviceDate = ServiceDate.parseString("20160623");
    List<Block> blocks = new ArrayList<Block>();
    blocks.add(l.getBlockBySequence(4237386)); // saturday service
    
    Block b = l.filterActiveBlock(blocks, serviceDate);
    assertNull(b); // should not be active on a Thursday
    
    blocks.clear();
    blocks.add(l.getBlockBySequence(4237399)); // sunday service
    b = l.filterActiveBlock(blocks, serviceDate);
    assertNull(b); // not active on Thusrday
    
    blocks.clear();
    blocks.add(l.getBlockBySequence(4237416)); // weekday service
    b = l.filterActiveBlock(blocks, serviceDate);
    assertNotNull(b); // weekday active on Thursday

    blocks.clear();
    blocks.add(l.getBlockBySequence(4237414)); // weekday service
    b = l.filterActiveBlock(blocks, serviceDate);
    assertNotNull(b); // weekday active on Thursday

    // TODO: test calendar exception support
  }

  @Test
  public void testIsDayActive() throws Exception {
    // sunday service
    ServiceCalendar calendar = l.getCalendarByServiceId("14497");
    ServiceDate serviceDate = ServiceDate.parseString("20160623");
    assertFalse(l.isDayActive(calendar, serviceDate));
    // saturday service
    calendar = l.getCalendarByServiceId("12204");
    assertFalse(l.isDayActive(calendar, serviceDate));
    // weekday service
    calendar = l.getCalendarByServiceId("64700");
    assertTrue(l.isDayActive(calendar, serviceDate));
  }
}
