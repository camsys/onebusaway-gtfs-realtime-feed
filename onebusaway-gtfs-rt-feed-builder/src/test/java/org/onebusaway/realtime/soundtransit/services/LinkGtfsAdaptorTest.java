package org.onebusaway.realtime.soundtransit.services;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

public class LinkGtfsAdaptorTest {
  
  private LinkGtfsAdaptor l;
  @Before
  public void setup() throws Exception {
    l = new LinkGtfsAdaptor("LinkJune2016", "20160623");
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
  
  @Test
  public void testFindBestTrip() throws Exception {
    ServiceDate serviceDate = ServiceDate.parseString("20160623");
    assertNotNull("block 4237414 not active on scheduleTime of " 
    + new Date(1466694600000l) + " and serviceDate=" + serviceDate,
    l.findBestTrip("4237414", 1466694600000l, serviceDate));
  }
}
