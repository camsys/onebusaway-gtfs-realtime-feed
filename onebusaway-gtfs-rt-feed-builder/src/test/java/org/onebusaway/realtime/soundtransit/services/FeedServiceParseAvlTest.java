/**
 * Copyright (C) 2015 Brian Ferris <bdferris@onebusaway.org>
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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.TripInfo;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedServiceParseAvlTest {
  
  private static final Logger _log = LoggerFactory.getLogger(FeedServiceParseAvlTest.class);

  private static final String LINK_AVL_DATA_1 = "src/test/resources/LinkAvlData.txt";
  private static final String LINK_AVL_DATA_2 = "src/test/resources/LinkAvlData_2.txt";
  
  private static FeedServiceImpl _feedService = null;
  
  private static LinkAVLData parsedLinkAVLData_1;
  private static LinkAVLData parsedLinkAVLData_2;
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    _feedService = new FeedServiceImpl();
    parsedLinkAVLData_1 = parseAVLDataFromFile(LINK_AVL_DATA_1);
    parsedLinkAVLData_2 = parseAVLDataFromFile(LINK_AVL_DATA_2);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testParseAVLFeed() {
    // Verify number of trips in test data, should be three
    TripInfoList tripInfoList = parsedLinkAVLData_1.getTrips();
    List<TripInfo> trips = tripInfoList.getTrips();
    assertEquals(3, trips.size());
    
    // Verify number of stop updates for each trip
    TripInfo trip_1 = trips.get(0);
    assertEquals(2, trip_1.getStopUpdates().getUpdates().size());

    TripInfo trip_2 = trips.get(1);
    assertEquals(9, trip_2.getStopUpdates().getUpdates().size());

    TripInfo trip_3 = trips.get(2);
    assertEquals(7, trip_3.getStopUpdates().getUpdates().size());
  }

  @Test
  public void testParseAVLFeedMissingStopUpdates() {
    // Verify number of trips in test data, should be three
    TripInfoList tripInfoList = parsedLinkAVLData_2.getTrips();
    List<TripInfo> trips = tripInfoList.getTrips();
    assertEquals(3, trips.size());
    
    // Verify number of stop updates for each trip
    // trip_1 has no stop updates in the feed, so the List of StopUpdates should be null
    TripInfo trip_1 = trips.get(0);
    assertNull(trip_1.getStopUpdates().getUpdates());
        
    TripInfo trip_2 = trips.get(1);
    assertEquals(9, trip_2.getStopUpdates().getUpdates().size());

    TripInfo trip_3 = trips.get(2);
    assertEquals(7, trip_3.getStopUpdates().getUpdates().size());
  }

  private static LinkAVLData parseAVLDataFromFile(String filename) {
    boolean fileExists = new File(filename).exists();
    assertTrue(fileExists);
    String linkAvlFeed = "";
    try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
      String nextLine = "";
      while ((nextLine = br.readLine()) != null) {
        linkAvlFeed = nextLine;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    LinkAVLData parsedLinkAVLData =  _feedService.parseAVLFeed(linkAvlFeed);
    return parsedLinkAVLData;
  }
}
