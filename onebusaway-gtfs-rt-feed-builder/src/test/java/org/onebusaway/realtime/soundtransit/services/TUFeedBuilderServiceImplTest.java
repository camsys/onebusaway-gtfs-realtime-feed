package org.onebusaway.realtime.soundtransit.services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.runtime.internal.cflowstack.ThreadCounterImpl11;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.onebusaway.transit_data_federation.impl.blocks.BlockRunServiceImpl;
import org.onebusaway.transit_data_federation.services.FederatedTransitDataBundle;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

import static org.junit.Assert.*;

public class TUFeedBuilderServiceImplTest {

  
  private static final int LINK_ROUTE_KEY = 599;
  private static final String LINK_ROUTE_ID = "100479";

  private static final Map<String, Integer> blockTripStartTimeMap = new HashMap<String, Integer>(); 
  
  @Before
  public void setup() {
    
  }
  
  @Test
  public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
    AvlParseService avlParseService = new AvlParseServiceImpl();
    
    TUFeedBuilderServiceImpl impl = new TUFeedBuilderServiceImpl();
    FederatedTransitDataBundle bundle = new FederatedTransitDataBundle();
    bundle.setPath(new File(System.getProperty("java.io.tmpdir")));
    BlockRunServiceImpl blockRunService = new BlockRunServiceImpl();
    blockRunService.setBundle(bundle);
    blockRunService.setup();
    buildRunBlocks(blockRunService);
    buildTripStartTimeMap(blockTripStartTimeMap);
    final Map<String, String> tripDirectionMap = buildTripDirectionMap();
    
    LinkTripServiceImpl linkTripService = new LinkTripServiceImpl(){

      // override lookupTrip to not require a bundle
      String lookupTrip(String blockRunStr, Long scheduleTime) {
        assertTrue("something is wrong with blockRunStr " + blockRunStr,
            !scheduleTime.equals(new Long(0L)));
        List<AgencyAndId> blockIds = lookupBlockIds(blockRunStr);
        assertNotNull("expected block for run " + blockRunStr, blockIds);
        assertTrue(blockIds.size() > 0);
        String debugBlockId = "";
        Integer trip = null;
        for (AgencyAndId agencyBlockId : blockIds) {
          String blockId = agencyBlockId.getId();
          debugBlockId += blockId + ", ";
          trip = blockTripStartTimeMap.get(blockId + ":" + scheduleTime);
          if (trip != null) break; // we found it
        }

        // make sure we always find a trip from the above map
        assertNotNull("blockId= " + debugBlockId + ", " 
            + "blockRun=" + blockRunStr 
            + " and scheduleTime=" 
            + scheduleTime + " (" + new Date(scheduleTime) + ")"
            , trip);
        return trip.toString();
      }
      
      public String getTripDirectionFromTripId(String tripId) {
        String direction = tripDirectionMap.get(tripId);
        assertNotNull("missing direction for tripId=" + tripId, direction);
        return direction;
      }
    };
    
    FeedServiceImpl _feedService = null;
    _feedService = new FeedServiceImpl();
    _feedService.setAvlParseService(avlParseService);
    _feedService.setTuFeedBuilderServiceImpl(impl);
    AVLDataParser avldp = new AVLDataParser(_feedService);

    
    linkTripService.setBlockRunSerivce(blockRunService);
    linkTripService.setTimeToUpdateTripIds(Long.MAX_VALUE); //To prevent update
    TransitGraphDao _transitGraphDao = Mockito.mock(TransitGraphDao.class);
    linkTripService.setTransitGraphDao(_transitGraphDao);
    LinkStopServiceImpl linkStopService = new LinkStopServiceImpl();
    linkStopService.setStopMapping(avldp.buildStopMapping(AVLDataParser.STOP_MAPPING_FILE));
    linkStopService.setNbStopOffsets(avldp.buildNbStopOffsets());
    linkStopService.setSbStopOffsets(avldp.buildSbStopOffsets());
    impl.setLinkStopServiceImpl(linkStopService);
    linkTripService.setLinkStopServiceImpl(linkStopService);
    linkTripService.setLinkRouteId(LINK_ROUTE_ID);
    List<TripEntry> allTrips = new ArrayList<TripEntry>();
    linkTripService.setTripEntries(allTrips);
    impl.setLinkTripServiceImpl(linkTripService);
    
    LinkAVLData linkAVLData = avldp.parseAVLDataFromFile(AVLDataParser.LINK_AVL_DATA_3);
    assertNotNull(linkAVLData);
    
    _feedService.setFrequencySchedule("false");
    FeedMessage feedMessage = _feedService.buildTUMessage(linkAVLData);
    assertNotNull(feedMessage);
   assertEquals(16, feedMessage.getEntityCount());
   
   // first entity
   FeedEntity e1 = feedMessage.getEntity(0);
   // trip 31625833
   assertEquals(linkAVLData.getTrips().getTrips().get(0).getVehicleId(), e1.getId());
   assertTrue(e1.hasTripUpdate());
   assertTrue(e1.getTripUpdate().hasTrip());
   assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:09:28.467-07:00"), e1.getTripUpdate().getTimestamp());
   TripDescriptor td1 = e1.getTripUpdate().getTrip();
   assertEquals(TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
   assertTrue(td1.hasTripId());
   assertEquals("31625833", td1.getTripId());
   assertTrue(e1.getTripUpdate().hasVehicle());
   assertEquals("134:138", e1.getTripUpdate().getVehicle().getId());
   // we only want one update
   assertEquals(1, e1.getTripUpdate().getStopTimeUpdateCount());
   StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
   assertTrue(e1st1.hasArrival());
   // update needs to be in seconds, not millis!
   long e1st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:11:00.000-07:00");
   assertEquals(e1st1ArrivalTime, e1st1.getArrival().getTime());
   
   
   // second entity
   FeedEntity e2 = feedMessage.getEntity(1);
   TripDescriptor td2 = e2.getTripUpdate().getTrip();
   assertTrue(td2.hasTripId());
   assertEquals("31625834", td2.getTripId());
   
   // third entity
   FeedEntity e3 = feedMessage.getEntity(2);
   TripDescriptor td3 = e3.getTripUpdate().getTrip();
   assertTrue(td3.hasTripId());
   assertEquals("31625963", td3.getTripId());
   
   
   // trip "11: 390" is running early, verify the estimated and not the scheduled time comes through
   FeedEntity e4 = findByVehicleId(feedMessage, "111:145");
   assertNotNull(e4);
   assertEquals(1466694620, e4.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime()); //2016-06-23T08:10:20.000-07:00
   
   // validate the remaining updates and verify the estimated times are greater (in the future)
   // as compared to the lastUpdatedDate
   for (FeedEntity e : feedMessage.getEntityList()) {
     // only 1 update for each entity
     assertEquals(1, e.getTripUpdate().getStopTimeUpdateCount());
     StopTimeUpdate stu11 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
     assertTrue(stu11.getArrival().getTime() > e.getTripUpdate().getTimestamp());
   }
  }


  private FeedEntity findByVehicleId(FeedMessage feedMessage, String search) {
    for (FeedEntity fe : feedMessage.getEntityList()) {
      if (search.equals(fe.getTripUpdate().getVehicle().getId())) {
        return fe;
      }
    }
    return null;
  }

  private Map<String, String> buildTripDirectionMap() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("31625833", "0");
    map.put("31625834", "0");
    map.put("31625835", "0");
    map.put("31625844", "0");
    map.put("31625845", "0");
    map.put("31625847", "0");
    
    // 31625936 is first "1"
    
    map.put("31625944", "1");
    map.put("31625945", "1");
    map.put("31625946", "1");
    map.put("31625954", "1");
    map.put("31625955", "1");
    map.put("31625963", "1");
    map.put("31626060", "1");
    
    // 31626070 back to "0"
    
    map.put("31626079", "0");
    map.put("31626111", "0");
    return map;
  }

  private void buildTripStartTimeMap(
      Map<String, Integer> blocktripstarttimemap2) {
    blockTripStartTimeMap.put("4237414:1466694600000", 31625834);
    blockTripStartTimeMap.put("4237416:1466692740000", 31625833);
    blockTripStartTimeMap.put("4237419:1466692020000", 31625963);
    blockTripStartTimeMap.put("4237429:1466692380000", 31626079); // start of trip
    blockTripStartTimeMap.put("4237410:1466693820000", 31625845);
    blockTripStartTimeMap.put("4237428:1466694540000", 31626111); //first actual is missing
    blockTripStartTimeMap.put("4237411:1466694060000", 31625847);
    blockTripStartTimeMap.put("4237413:1466694060000", 31625945);
    blockTripStartTimeMap.put("4237431:1466694420000", 31626060);
    blockTripStartTimeMap.put("4237407:1466693100000", 31625844);
    blockTripStartTimeMap.put("4237417:1466693460000", 31625835);
    blockTripStartTimeMap.put("4237417:1466694180000", 31625835); // duplicate trip!
    blockTripStartTimeMap.put("4237420:1466694090000", 31625944);
    blockTripStartTimeMap.put("4237409:1466694120000", 31625954);
    blockTripStartTimeMap.put("4237418:1466694240000", 31625955);
    blockTripStartTimeMap.put("4237415:1466694060000", 31625946);
  }

  private void buildRunBlocks(BlockRunServiceImpl blockRunService) {
    // this data is based off of KCM "6/16/2016  2:31 PM     12678719 google_daily_transit.zip"
    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237386);
    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237399);
    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237416);

    blockRunService.addRunBlock(2, LINK_ROUTE_KEY, 4237387);
    blockRunService.addRunBlock(2, LINK_ROUTE_KEY, 4237398);
    blockRunService.addRunBlock(2, LINK_ROUTE_KEY, 4237407);

    blockRunService.addRunBlock(3, LINK_ROUTE_KEY, 4237391);
    blockRunService.addRunBlock(3, LINK_ROUTE_KEY, 4237405);
    blockRunService.addRunBlock(3, LINK_ROUTE_KEY, 4237417);

    blockRunService.addRunBlock(4, LINK_ROUTE_KEY, 4237391);
    blockRunService.addRunBlock(4, LINK_ROUTE_KEY, 4237405);
    blockRunService.addRunBlock(4, LINK_ROUTE_KEY, 4237417);
    
    blockRunService.addRunBlock(5, LINK_ROUTE_KEY, 4237395);
    blockRunService.addRunBlock(5, LINK_ROUTE_KEY, 4237396);
    blockRunService.addRunBlock(5, LINK_ROUTE_KEY, 4237408);
    
    blockRunService.addRunBlock(6, LINK_ROUTE_KEY, 4237388);
    blockRunService.addRunBlock(6, LINK_ROUTE_KEY, 4237397);
    blockRunService.addRunBlock(6, LINK_ROUTE_KEY, 4237420);

    blockRunService.addRunBlock(7, LINK_ROUTE_KEY, 4237389);
    blockRunService.addRunBlock(7, LINK_ROUTE_KEY, 4237400);
    blockRunService.addRunBlock(7, LINK_ROUTE_KEY, 4237409);

    blockRunService.addRunBlock(8, LINK_ROUTE_KEY, 4237390);
    blockRunService.addRunBlock(8, LINK_ROUTE_KEY, 4237402);
    blockRunService.addRunBlock(8, LINK_ROUTE_KEY, 4237418);

    blockRunService.addRunBlock(9, LINK_ROUTE_KEY, 4237394);
    blockRunService.addRunBlock(9, LINK_ROUTE_KEY, 4237403);
    blockRunService.addRunBlock(9, LINK_ROUTE_KEY, 4237415);

    
    blockRunService.addRunBlock(10, LINK_ROUTE_KEY, 4237392);
    blockRunService.addRunBlock(10, LINK_ROUTE_KEY, 4237406);
    blockRunService.addRunBlock(10, LINK_ROUTE_KEY, 4237414);
    
    blockRunService.addRunBlock(11, LINK_ROUTE_KEY, 4237393);
    blockRunService.addRunBlock(11, LINK_ROUTE_KEY, 4237404);
    blockRunService.addRunBlock(11, LINK_ROUTE_KEY, 4237419);
    
    blockRunService.addRunBlock(12, LINK_ROUTE_KEY, 4237429);
    
    blockRunService.addRunBlock(13, LINK_ROUTE_KEY, 4237410);
    
    blockRunService.addRunBlock(14, LINK_ROUTE_KEY, 4237428);
    
    blockRunService.addRunBlock(15, LINK_ROUTE_KEY, 4237411);
    
    blockRunService.addRunBlock(16, LINK_ROUTE_KEY, 4237413);
    
    blockRunService.addRunBlock(17, LINK_ROUTE_KEY, 4237431);

  }

  /**
   * Train 15 does not report a run number and therefore comes across as unscheduled.
   * Confirm a trip is selected via the actual time of the next stop
   */
  @Test
  public void testBuildScheduleFeedMessage1() {
  }

  /**
   * Run 6 has two trains reporting on it.  Confirm the are both assigned
   * trips.
   */
  @Test
  public void testBuildScheduleFeedMessage2() {
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
