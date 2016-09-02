package org.onebusaway.realtime.soundtransit.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.transit_data_federation.impl.blocks.BlockRunServiceImpl;
import org.onebusaway.transit_data_federation.services.FederatedTransitDataBundle;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

public class TUFeedBuilderServiceImplTest {

  private static final String LINK_ROUTE_ID = "100479";
  
  private LinkGtfsAdaptor ga;
  private FeedServiceImpl _feedService;
  private AVLDataParser avldp ;
  private AvlParseService avlParseService = new AvlParseServiceImpl();
  
  @Before
  public void setup() throws Exception {
    // this data is based off of KCM "6/16/2016  2:31 PM     12678719 google_daily_transit.zip"
    ga = new LinkGtfsAdaptor("LinkJune2016", "20160623");
    
    
    TUFeedBuilderServiceImpl impl = new TUFeedBuilderServiceImpl();
    TUFeedBuilderScheduleServiceImpl simpl = new TUFeedBuilderScheduleServiceImpl();
    TUFeedBuilderComponent comp = new TUFeedBuilderComponent();
    simpl.setTUFeedBuilderComponent(comp);
    impl.setTUFeedBuilderScheduleServiceImpl(simpl);
    FederatedTransitDataBundle bundle = new FederatedTransitDataBundle();
    bundle.setPath(new File(System.getProperty("java.io.tmpdir")));
    BlockRunServiceImpl blockRunService = new BlockRunServiceImpl();
    blockRunService.setBundle(bundle);
    blockRunService.setup();
    buildRunBlocks(blockRunService);
    
    LinkTripServiceImpl linkTripService = new TestLinkTripServiceImpl(ga);
    
    _feedService = new FeedServiceImpl();
    _feedService.setAvlParseService(avlParseService);
    _feedService.setTuFeedBuilderServiceImpl(impl);
    avldp = new AVLDataParser(_feedService);
    
    linkTripService.setBlockRunSerivce(blockRunService);
    linkTripService.setTimeToUpdateTripIds(Long.MAX_VALUE); //To prevent update
    TransitGraphDao _transitGraphDao = Mockito.mock(TransitGraphDao.class);
    linkTripService.setTransitGraphDao(_transitGraphDao);
    LinkStopServiceImpl linkStopService = new LinkStopServiceImpl();
    linkStopService.setStopMapping(avldp.buildStopMapping(AVLDataParser.STOP_MAPPING_FILE));
    linkStopService.setNbStopOffsets(avldp.buildNbStopOffsets());
    linkStopService.setSbStopOffsets(avldp.buildSbStopOffsets());
    impl.setLinkStopServiceImpl(linkStopService);
    simpl.setLinkStopServiceImpl(linkStopService);
    comp.setLinkStopServiceImpl(linkStopService);
    linkTripService.setLinkStopServiceImpl(linkStopService);
    linkTripService.setLinkRouteId(LINK_ROUTE_ID);
    List<TripEntry> allTrips = new ArrayList<TripEntry>();
    linkTripService.setTripEntries(allTrips);
    impl.setLinkTripServiceImpl(linkTripService);
    simpl.setLinkTripServiceImpl(linkTripService);
    comp.setLinkTripServiceImpl(linkTripService);
  }
  
  @Test
  public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException {
    
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
   assertTrue(e1.getTripUpdate().hasDelay());
   assertEquals(0, e1.getTripUpdate().getDelay());
   assertTrue(e1.getTripUpdate().hasTrip());
   assertEquals(new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:09:28.467-07:00"), e1.getTripUpdate().getTimestamp());
   TripDescriptor td1 = e1.getTripUpdate().getTrip();
   
   assertEquals(TripDescriptor.ScheduleRelationship.SCHEDULED, td1.getScheduleRelationship());
   assertTrue(e1.getTripUpdate().hasVehicle());
   assertEquals("1: 6", e1.getTripUpdate().getVehicle().getId());

   assertTrue(td1.hasTripId());
   Block b = ga.getBlockForRun(linkAVLData.getTrips().getTrips().get(0).getTripId().split(":")[0], ga.getServiceDate());
   assertEquals(4237416, b.getBlockSequence());
   // tripId "1: 6" has run of 1 and via block.txt has block of 4237416
   assertEquals("31625834", td1.getTripId());
   // we want multiple updates!
   assertEquals(3, e1.getTripUpdate().getStopTimeUpdateCount());
   StopTimeUpdate e1st1 = e1.getTripUpdate().getStopTimeUpdateList().get(0);
   assertTrue(e1st1.hasArrival());
   // update needs to be in seconds, not millis!
   long e1st1ArrivalTime = new AvlParseServiceImpl().parseAvlTimeAsSeconds("2016-06-23T08:11:00.000-07:00");
   assertEquals(e1st1ArrivalTime, e1st1.getArrival().getTime());
   
   
   // second entity
   FeedEntity e2 = feedMessage.getEntity(1);
   TripDescriptor td2 = e2.getTripUpdate().getTrip();
   assertTrue(td2.hasTripId());
   assertEquals("31625956", td2.getTripId());
   
   // third entity
   FeedEntity e3 = feedMessage.getEntity(2);
   TripDescriptor td3 = e3.getTripUpdate().getTrip();
   assertTrue(td3.hasTripId());
   assertEquals("31625843", td3.getTripId());
   assertTrue(e3.hasTripUpdate());
   assertTrue(e3.getTripUpdate().hasDelay());
   /*
   "StopId": "SEA_PLAT",
   "StationName": "Airport Station",
   "Frequency": "0",
   "ArrivalTime": {
     "Actual": null,
     "Scheduled": "2016-06-23T08:11:00.000-07:00",
     "Estimated": "2016-06-23T08:10:20.000-07:00"
   }
   */
   assertEquals(td3.getTripId() + " has invalid delay", -40, e3.getTripUpdate().getDelay());
   
   
   // trip "11: 390" is running early, verify the estimated and not the scheduled time comes through
   FeedEntity e4 = findByVehicleId(feedMessage, "11: 390");
   assertNotNull(e4);
   assertEquals(1466694620, e4.getTripUpdate().getStopTimeUpdateList().get(0).getArrival().getTime()); //2016-06-23T08:10:20.000-07:00
   
   // validate the remaining updates and verify the estimated times are greater (in the future)
   // as compared to the lastUpdatedDate
   for (FeedEntity e : feedMessage.getEntityList()) {
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


  private void buildRunBlocks(BlockRunServiceImpl blockRunService) {
    
    for (Block block : ga.getAllBlocks()) {
      blockRunService.addRunBlock(block.getBlockRun(), block.getBlockRoute(), block.getBlockSequence());
    }
//   example run blocks
//    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237386); sat
//    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237399); sun
//    blockRunService.addRunBlock(1, LINK_ROUTE_KEY, 4237416); weekday
  }
  
  long parseTime(String dateStr) throws Exception {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.parse(dateStr).getTime();
  }

  /**
   * Train 15 does not report a run number and therefore comes across as unscheduled.
   * Confirm a trip is selected via the actual time of the next stop
   */
//  @Test
  public void testBuildScheduleFeedMessage1() {
  }

  /**
   * Run 6 has two trains reporting on it.  Confirm the are both assigned
   * trips.
   */
//  @Test
  public void testBuildScheduleFeedMessage2() {
  }

  // override the lookups to not need a bundle, but to use the GTFS instead
  private static class TestLinkTripServiceImpl extends LinkTripServiceImpl {
    private LinkGtfsAdaptor ga;
    public TestLinkTripServiceImpl(LinkGtfsAdaptor ga) {
      this.ga = ga;
    }


    ScheduledBlockLocation lookupBlockLocation(String blockRunNumber, Long scheduleTime, String avlDirection, ServiceDate serviceDate) {
      int scheduleOffset = (int) (scheduleTime - serviceDate.getAsDate().getTime())/1000;
      Block b = ga.getBlockForRun(blockRunNumber, serviceDate);
      assertNotNull(b);
      Trip trip = ga.getTripByBlockId("" + b.getId());
      int lastArrivalOffset = Integer.MAX_VALUE;
      for (StopTime st : ga.getStopTimesForTripId(trip.getId().getId())) {
        if (st.isArrivalTimeSet() 
            && st.getArrivalTime() < lastArrivalOffset 
            && st.getArrivalTime() < scheduleOffset) {
          lastArrivalOffset = st.getArrivalTime();
        }
      }
      
      ScheduledBlockLocation sbl = new ScheduledBlockLocation();
      sbl.setNextStopTimeOffset(lastArrivalOffset);
      return sbl;
    }
    

    // override lookupTrip to not require a bundle
    String lookupTripByRunId(String blockRunStr, Long scheduleTime, String avlDirection, ServiceDate serviceDate) {
      assertTrue("something is wrong with blockRunStr " + blockRunStr,
          !scheduleTime.equals(new Long(0L)));
      List<AgencyAndId> blockIds = lookupBlockIds(blockRunStr);
      assertNotNull("expected block for run " + blockRunStr, blockIds);
      assertTrue(blockIds.size() > 0);
      String debugBlockId = "";
      String tripId = null;
      for (AgencyAndId agencyBlockId : blockIds) {
        String blockId = agencyBlockId.getId();
        debugBlockId += blockId + ", ";
        tripId = verifyTrip(blockId, scheduleTime, serviceDate);
        if (tripId != null) break; // we found it
      }
      // make sure we always find a trip from the above map
      assertNotNull("blockId= " + debugBlockId + ", " 
          + "blockRun=" + blockRunStr 
          + " and scheduleTime=" 
          + scheduleTime + " (" + new Date(scheduleTime) + ")"
          , tripId);
      return tripId.toString();
    }
    
    private String verifyTrip(String blockId, Long scheduleTime, ServiceDate serviceDate) {
      
      AgencyAndId gtfsTripId = ga.findBestTrip(blockId, scheduleTime, serviceDate);
      if (gtfsTripId == null) return null;
      return gtfsTripId.getId();
    }
  }
}
