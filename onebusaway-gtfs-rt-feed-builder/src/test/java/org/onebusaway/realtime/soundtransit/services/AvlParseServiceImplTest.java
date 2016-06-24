package org.onebusaway.realtime.soundtransit.services;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class AvlParseServiceImplTest {

  
  private static AvlParseServiceImpl impl = new AvlParseServiceImpl(); 
  
  @Test
  public void testParseAvlTime() {
    assertEquals(new Date("Thu Jun 23 10:39:00 EDT 2016"), impl.parseAvlTime("2016-06-23T07:39:00.000-07:00"));
  }
  
  @Test
  public void testParseAvlTimeAsSeconds() {
    assertEquals(new Date("Thu Jun 23 10:39:00 EDT 2016"), 
        new Date(impl.parseAvlTimeAsSeconds("2016-06-23T07:39:00.000-07:00") * 1000));
    assertEquals(1466692740l, impl.parseAvlTimeAsSeconds("2016-06-23T07:39:00.000-07:00"));
  }
  
  @Test
  public void testParseAvlTimeAsMillis() {
    assertEquals(1466692740000l, impl.parseAvlTimeAsMillis("2016-06-23T07:39:00.000-07:00"));
  }
}
