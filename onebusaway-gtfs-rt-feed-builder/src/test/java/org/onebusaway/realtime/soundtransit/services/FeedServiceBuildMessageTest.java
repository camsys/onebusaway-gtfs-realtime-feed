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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

public class FeedServiceBuildMessageTest {

  private static final Logger _log = LoggerFactory.getLogger(FeedServiceParseAvlTest.class);
  private static final String LINK_AVL_DATA_1 = "src/test/resources/LinkAvlData.txt";
  private static final String LINK_AVL_DATA_2 = "src/test/resources/LinkAvlData_2.txt";
  private static FeedServiceImpl _feedService = null;
  
  private static LinkAVLData parsedLinkAVLData_1;
  private static LinkAVLData parsedLinkAVLData_2;
  private static FeedMessage vehiclePositionsFM_1;
  private static FeedMessage vehiclePositionsFM_2;
  private static FeedMessage tripUpdatesFM_1;
  private static FeedMessage tripUpdatesFM_2;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    _feedService = new FeedServiceImpl();
    parsedLinkAVLData_1 = parseAVLDataFromFile(LINK_AVL_DATA_1);
    vehiclePositionsFM_1 = _feedService.buildVPMessage(parsedLinkAVLData_1);
    tripUpdatesFM_1 = _feedService.buildTUMessage(parsedLinkAVLData_1);
    
    parsedLinkAVLData_2 = parseAVLDataFromFile(LINK_AVL_DATA_2);
    vehiclePositionsFM_2 = _feedService.buildVPMessage(parsedLinkAVLData_2);
    tripUpdatesFM_2 = _feedService.buildTUMessage(parsedLinkAVLData_2);
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
  public void testBuildVPMessage() {
    assertEquals(3, vehiclePositionsFM_2.getEntityCount());
    List<FeedEntity> vehicleList = vehiclePositionsFM_1.getEntityList();
    for (FeedEntity vehicle : vehicleList) {
      VehiclePosition vp = vehicle.getVehicle();
      String startDate = vp.getTrip().getStartDate();
      assertEquals("20151201", startDate);
      float latitude = vp.getPosition().getLatitude();
      assertNotNull(latitude);
      String vehicleId = vp.getVehicle().getId();
      assertNotNull(vehicleId);
      String stopId = vp.getStopId();
      assertNotNull(stopId);
    }
  }

  @Test
  public void testBuildVPMessageMissingStopUpdates() {
    assertEquals(3, vehiclePositionsFM_2.getEntityCount());
    List<FeedEntity> vehicleList = vehiclePositionsFM_2.getEntityList();
    for (FeedEntity vehicle : vehicleList) {
      VehiclePosition vp = vehicle.getVehicle();
      float latitude = vp.getPosition().getLatitude();
      assertNotNull(latitude);
      String vehicleId = vp.getVehicle().getId();
      _log.info("vehicleId: " + vehicleId);
      assertNotNull(vehicleId);
      String stopId = vp.getStopId();
      assertNotNull(stopId);
//      String startDate = vp.getTrip().getStartDate();
//      _log.info("start date: " + startDate);
//      if (vehicleId.equals("156:102")) {
//        assertTrue(startDate.isEmpty());
//      } else {
//        assertEquals("20151201", startDate);
//      }
    }
  }

  @Test
  public void testBuildTUMessage() {
    int tripCt = tripUpdatesFM_1.getEntityCount();
    _log.info("Trip count 1: " + tripCt);
    
    List<FeedEntity> tripList = tripUpdatesFM_1.getEntityList();
    for (FeedEntity trip : tripList) {
      TripUpdate tu = trip.getTripUpdate();
      String tripId = tu.getTrip().getTripId();
      assertNotNull(tripId);
      String routeId = tu.getTrip().getRouteId();
      assertNotNull(routeId);
      int stopTimeUpdateCt = tu.getStopTimeUpdateCount();
      assertTrue(stopTimeUpdateCt>0);
      long timestamp = tu.getTimestamp();
      assertTrue(timestamp > 0);
    }
  }

  @Test
  public void testBuildTUMessageMissingStopUpdates() {
    int tripCt = tripUpdatesFM_2.getEntityCount();
    _log.info("Trip count 2: " + tripCt);
    
    List<FeedEntity> tripList = tripUpdatesFM_2.getEntityList();
    for (FeedEntity trip : tripList) {
      TripUpdate tu = trip.getTripUpdate();
      String tripId = tu.getTrip().getTripId();
      assertNotNull(tripId);
      String routeId = tu.getTrip().getRouteId();
      assertNotNull(routeId);
      int stopTimeUpdateCt = tu.getStopTimeUpdateCount();
      if (tripId.equals("6: 180")) {
        assertTrue(stopTimeUpdateCt == 0);
      } else {
        assertTrue(stopTimeUpdateCt>0);
      }
     long timestamp = tu.getTimestamp();
      assertTrue(timestamp > 0);
    }
  }
  
  private static LinkAVLData parseAVLDataFromFile(String filename) {
    boolean fileExists = new File(filename).exists();
    assertTrue(fileExists);
    String linkAvlFeed = "";
    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String nextLine = "";
      while ((nextLine = br.readLine()) != null) {
        linkAvlFeed = nextLine;
      }
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    _log.info("AVL data: " + linkAvlFeed);
    
    LinkAVLData parsedLinkAVLData =  _feedService.parseAVLFeed(linkAvlFeed);
    return parsedLinkAVLData;
  }

}
