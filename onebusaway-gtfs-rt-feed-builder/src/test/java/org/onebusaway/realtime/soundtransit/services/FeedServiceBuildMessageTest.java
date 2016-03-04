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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.transit_data_federation.impl.transit_graph.BlockConfigurationEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.BlockEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.FrequencyEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.RouteEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.TripEntryImpl;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

public class FeedServiceBuildMessageTest {

  private static final Logger _log = LoggerFactory.getLogger(FeedServiceParseAvlTest.class);
  private static final String LINK_AVL_DATA_1 = "src/test/resources/LinkAvlData.txt";
  private static final String LINK_AVL_DATA_2 = "src/test/resources/LinkAvlData_2.txt";
  private static final String STOP_MAPPING_FILE = "src/test/resources/LinkStopMapping.txt";
  private static FeedServiceImpl _feedService = null;
  private static TransitGraphDao _transitGraphDao;
  private static LinkTripServiceImpl linkTripService;
  private static LinkStopServiceImpl linkStopService;
  private static AvlParseServiceImpl avlParseService;
  private static VPFeedBuilderServiceImpl vpFeedBuilderServiceImpl;
  private static TUFeedBuilderServiceImpl tuFeedBuilderServiceImpl;
  private static LinkAVLData parsedLinkAVLData_1;
  private static LinkAVLData parsedLinkAVLData_2;
  private static FeedMessage vehiclePositionsFM_1;
  private static FeedMessage vehiclePositionsFM_2;
  private static FeedMessage tripUpdatesFM_1;
  private static FeedMessage tripUpdatesFM_2;
  private static Map<String, String> stopMapping;
  private static List<StopOffset> nbStopOffsets = new ArrayList<>();
  private static List<StopOffset> sbStopOffsets = new ArrayList<>();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    _transitGraphDao = Mockito.mock(TransitGraphDao.class);
    List<TripEntry> allTrips = buildTripsForFrequencyTrips();
    Mockito.when(_transitGraphDao.getAllTrips()).thenReturn(allTrips);
    _feedService = new FeedServiceImpl();
    stopMapping = buildStopMapping(STOP_MAPPING_FILE);
    nbStopOffsets = buildNbStopOffsets();
    sbStopOffsets = buildSbStopOffsets();
    linkStopService = new LinkStopServiceImpl();
    linkStopService.setStopMapping(stopMapping);
    linkStopService.setNbStopOffsets(nbStopOffsets);
    linkStopService.setSbStopOffsets(sbStopOffsets);
    LinkStopServiceImpl spyLinkStopService = Mockito.spy(linkStopService);
    Mockito.doNothing().when(spyLinkStopService).updateStopOffsets(Mockito.any(List.class));

    linkTripService = new LinkTripServiceImpl();
    linkTripService.setTimeToUpdateTripIds(Long.MAX_VALUE); //To prevent update
    linkTripService.setTransitGraphDao(_transitGraphDao);
    linkTripService.setLinkStopServiceImpl(spyLinkStopService);
    linkTripService.setLinkRouteId("100479");
    linkTripService.setTripEntries(allTrips);
    
    avlParseService = new AvlParseServiceImpl();
    vpFeedBuilderServiceImpl = new VPFeedBuilderServiceImpl();
    vpFeedBuilderServiceImpl.setLinkTripServiceImpl(linkTripService);
    vpFeedBuilderServiceImpl.setLinkStopServiceImpl(linkStopService);
    tuFeedBuilderServiceImpl = new TUFeedBuilderServiceImpl();
    tuFeedBuilderServiceImpl.setLinkTripServiceImpl(linkTripService);
    tuFeedBuilderServiceImpl.setLinkStopServiceImpl(linkStopService);

    _feedService.setAvlParseService(avlParseService);
    _feedService.setVpFeedBuilderServiceImpl(vpFeedBuilderServiceImpl);
    _feedService.setTuFeedBuilderServiceImpl(tuFeedBuilderServiceImpl);

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
    assertEquals(3, vehiclePositionsFM_1.getEntityCount());
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
    assertEquals(2, vehiclePositionsFM_2.getEntityCount());
    List<FeedEntity> vehicleList = vehiclePositionsFM_2.getEntityList();
    for (FeedEntity vehicle : vehicleList) {
      VehiclePosition vp = vehicle.getVehicle();
      float latitude = vp.getPosition().getLatitude();
      assertNotNull(latitude);
      String vehicleId = vp.getVehicle().getId();
      assertNotNull(vehicleId);
      String stopId = vp.getStopId();
      assertNotNull(stopId);
      String startDate = vp.getTrip().getStartDate();
      if (vehicleId.equals("6: 180")) {
        assertTrue(startDate.isEmpty());
     } else {
        assertEquals("20151201", startDate);
      }
   
    }
  }

  @Test
  public void testBuildTUMessage() {
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
    List<FeedEntity> tripList = tripUpdatesFM_2.getEntityList();
    for (FeedEntity trip : tripList) {
      TripUpdate tu = trip.getTripUpdate();
      String tripId = tu.getTrip().getTripId();
      assertNotNull(tripId);
      String routeId = tu.getTrip().getRouteId();
      assertNotNull(routeId);
      int stopTimeUpdateCt = tu.getStopTimeUpdateCount();
      if (tripId.equals("29784221")) {
        assertTrue(stopTimeUpdateCt == 0);
      } else {
        assertTrue("stopTimUpdateCt should be greater than zero for trip " 
        + tripId + " with count of " + stopTimeUpdateCt, stopTimeUpdateCt>0);
      }
     long timestamp = tu.getTimestamp();
      assertTrue(timestamp > 0);
    }
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
  
  private static List<TripEntry> buildTripsForFrequencyTrips() {
    List<TripEntry> allTrips = new ArrayList<>();
    // Northbound trips, from Sea Tac Airport to downtown
    TripEntry t1 = buildTripEntry("40", "29784221", "29784221", "100479", "1", "05:00:00", "05:59:00", 900);
    TripEntry t2 = buildTripEntry("40", "29784225", "29784225", "100479", "1", "06:00:00", "08:39:00", 360);
    TripEntry t3 = buildTripEntry("40", "29784126", "29784126", "100479", "1", "08:40:00", "14:59:00", 600);
    TripEntry t4 = buildTripEntry("40", "29784159", "29784159", "100479", "1", "15:00:00", "18:29:00", 360);
    TripEntry t5 = buildTripEntry("40", "29784172", "29784172", "100479", "1", "18:30:00", "21:59:00", 600);
    TripEntry t6 = buildTripEntry("40", "29784206", "29784206", "100479", "1", "22:00:00", "25:00:00", 900);
    
    //Southbound trips, from downtown to Sea Tac Airport
    TripEntry t7 = buildTripEntry("40", "29784240", "29784240", "100479", "0", "05:00:00", "05:59:00", 900);
    TripEntry t8 = buildTripEntry("40", "29784239", "29784239", "100479", "0", "06:00:00", "08:39:00", 360);
    TripEntry t9 = buildTripEntry("40", "29784261", "29784261", "100479", "0", "08:40:00", "14:59:00", 600);
    TripEntry t10 = buildTripEntry("40", "29784050", "29784050", "100479", "0", "15:00:00", "18:29:00", 360);
    TripEntry t11 = buildTripEntry("40", "29784264", "29784264", "100479", "0", "18:30:00", "21:59:00", 600);
    TripEntry t12 = buildTripEntry("40", "29784267", "29784267", "100479", "0", "22:00:00", "25:00:00", 900);

    allTrips.add(t1);
    allTrips.add(t2);
    allTrips.add(t3);
    allTrips.add(t4);
    allTrips.add(t5);
    allTrips.add(t6);
    allTrips.add(t7);
    allTrips.add(t8);
    allTrips.add(t9);
    allTrips.add(t10);
    allTrips.add(t11);
    allTrips.add(t12);
    
    return allTrips;
  }
  
  private static TripEntry buildTripEntry(String agencyid, String tripId, String blockId, 
      String routeId, String directionId, String frequencyStartTime, 
      String frequencyEndTime, int headwaySeconds) {
    TripEntryImpl trip = new TripEntryImpl();
    AgencyAndId id = new AgencyAndId();
    id.setAgencyId(agencyid);
    id.setId(tripId);
    trip.setId(id);
    trip.setDirectionId(directionId);  // "1" is Northbound
    RouteEntryImpl route = new RouteEntryImpl();
    id.setId(routeId);
    route.setId(id);
    trip.setRoute(route);
    
    BlockEntryImpl block = new BlockEntryImpl();
    id.setId(blockId);
    block.setId(id);
    List<BlockConfigurationEntry> configurations = new ArrayList<>();
    List<FrequencyEntry> frequencies = new ArrayList<>();
    
    String formattedStartTime = frequencyStartTime;
    String formattedEndTime = frequencyEndTime;
    DateFormat df = new SimpleDateFormat("HH:mm:ss");
    Date parsedStartDate = null;
    Date parsedEndDate = null;
    try {
      parsedStartDate = df.parse(formattedStartTime);
      parsedEndDate = df.parse(formattedEndTime);
    } catch (Exception e) {
      _log.info("Exception parsing start time: " + formattedStartTime);
    }
    int startTime = (int)(parsedStartDate.getTime() / 1000);
    int endTime = (int)(parsedEndDate.getTime() / 1000);

    FrequencyEntry frequency = new FrequencyEntryImpl(startTime, endTime, headwaySeconds, 0);
    frequencies.add(frequency);
    
    BlockConfigurationEntryImpl.Builder configurationBuilder = BlockConfigurationEntryImpl.builder();
    BlockEntryImpl blockEntry = new BlockEntryImpl();
    id.setId(blockId);
    blockEntry.setId(id);
    configurationBuilder.setBlock(blockEntry);
    List<TripEntry> blockTrips = new ArrayList<>();
    configurationBuilder.setTrips(blockTrips);
    configurationBuilder.setFrequencies(frequencies);
    BlockConfigurationEntry blockConfiguration = configurationBuilder.create();
    configurations.add(blockConfiguration);
    block.setConfigurations(configurations);
    trip.setBlock(block);

    return trip;
  }
  
  private static Map<String, String> buildStopMapping(String stopMappingFile) {
    Map<String, String> stopMapping = null;

    // Read in the AVL-GTFS stop mapping file
    try (BufferedReader br = new BufferedReader(new FileReader(stopMappingFile))) {
      stopMapping = new HashMap<String, String>();
      String ln = "";
      while ((ln = br.readLine()) != null) {
        _log.info(ln);
        int idx = ln.indexOf(',');
        if (idx > 0) {
          stopMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
        }
      }
    } catch (IOException e) {
      _log.error("Error reading StopMapping file " + e.getMessage());
    }
    return stopMapping;
  }
  
  private static List<StopOffset> buildNbStopOffsets() {
    List<StopOffset> nbStopOffsets = new ArrayList<>();
    StopOffset offset = new StopOffset("99903", "SEA_PLAT", "1", 0);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99905", "NB782T", "1", 3);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55578", "NB484T", "1", 12);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55656", "NB435T", "1", 15);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55778", "NB331T", "1", 19);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55860", "NB260T", "1", 22);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99240", "NB215T", "1", 24);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99256", "NB153T", "1", 27);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99260", "NB117T", "1", 29);
    nbStopOffsets.add(offset);
    offset = new StopOffset("621", "NB1093T", "1", 31);
    nbStopOffsets.add(offset);
    offset = new StopOffset("532", "NB1075T", "1", 34);
    nbStopOffsets.add(offset);
    offset = new StopOffset("565", "NB1053T", "1", 36);
    nbStopOffsets.add(offset);
    offset = new StopOffset("1121", "NB1036T", "1", 38);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99602", "NB1083T", "1", 41);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99502", "NB1205T", "1", 44);
    nbStopOffsets.add(offset);
    
    return nbStopOffsets;
  }
  
  private static List<StopOffset> buildSbStopOffsets() {
    List<StopOffset> sbStopOffsets = new ArrayList<>();
    StopOffset offset = new StopOffset("99500", "SB1209T", "0", 0);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99600", "SB1088T", "0", 3);
    sbStopOffsets.add(offset);
    offset = new StopOffset("1108", "SB1029T", "0", 6);
    sbStopOffsets.add(offset);
    offset = new StopOffset("455", "SB1047T", "0", 8);
    sbStopOffsets.add(offset);
    offset = new StopOffset("501", "SB1070T", "0", 10);
    sbStopOffsets.add(offset);
    offset = new StopOffset("623", "SB1087T", "0", 13);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99101", "SB113T", "0", 15);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99111", "SB148T", "0", 17);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99121", "SB210T", "0", 20);
    sbStopOffsets.add(offset);
    offset = new StopOffset("55949", "SB255T", "0", 22);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56039", "SB312T", "0", 25);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56159", "SB417T", "0", 29);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56173", "SB469T", "0", 32);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99900", "SB778T", "0", 41);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99904", "SEA_PLAT", "0", 44);
    sbStopOffsets.add(offset);
    
    return sbStopOffsets;
  }

}
