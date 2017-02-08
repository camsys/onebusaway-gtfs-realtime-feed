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

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import org.mockito.Mockito;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.realtime.soundtransit.services.AvlParseService;
import org.onebusaway.realtime.soundtransit.services.AvlParseServiceImpl;
import org.onebusaway.realtime.soundtransit.services.FeedServiceImpl;
import org.onebusaway.realtime.soundtransit.services.LinkStopServiceImpl;
import org.onebusaway.realtime.soundtransit.services.LinkTripServiceImpl;
import org.onebusaway.realtime.soundtransit.services.TUFeedBuilderComponent;
import org.onebusaway.realtime.soundtransit.services.TUFeedBuilderScheduleServiceImpl;
import org.onebusaway.realtime.soundtransit.services.TUFeedBuilderServiceImpl;
import org.onebusaway.transit_data_federation.impl.blocks.BlockRunServiceImpl;
import org.onebusaway.transit_data_federation.services.FederatedTransitDataBundle;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;

public abstract class AbstractFeedBuilderTest {

  protected static Logger _log = LoggerFactory.getLogger(AbstractFeedBuilderTest.class);

  private static final String LINK_ROUTE_ID = "100479";
  
  protected LinkGtfsAdaptor ga;
  protected FeedServiceImpl _feedService;
  protected AVLDataParser avldp ;
  protected AvlParseService avlParseService = new AvlParseServiceImpl();
  protected Long _referenceTime = null;
  

  public AbstractFeedBuilderTest(String bundleDir, String serviceDateStr, Long referenceTime, boolean legacyStopMapping) throws Exception {
    _referenceTime = referenceTime;
    ga = new LinkGtfsAdaptor(bundleDir, serviceDateStr);
    
    
    TUFeedBuilderServiceImpl impl = new TUFeedBuilderServiceImpl();
    TUFeedBuilderScheduleServiceImpl simpl = new TUFeedBuilderScheduleServiceImpl();
    simpl.setOverrideLastUpdatedDate(false);
    TUFeedBuilderComponent comp = new TUFeedBuilderComponent();
    simpl.setTUFeedBuilderComponent(comp);
    impl.setTUFeedBuilderScheduleServiceImpl(simpl);
    FederatedTransitDataBundle bundle = new FederatedTransitDataBundle();
    bundle.setPath(new File(System.getProperty("java.io.tmpdir")));
    BlockRunServiceImpl blockRunService = new BlockRunServiceImpl();
    blockRunService.setBundle(bundle);
    blockRunService.setup();
    buildRunBlocks(blockRunService);

    GtfsTransitDataServiceFacade tds = new GtfsTransitDataServiceFacade(ga, serviceDateStr, _referenceTime);
    LinkTripServiceImpl linkTripService = new LinkTripServiceImpl();
    linkTripService.setTransitDataServiceFacade(tds);
    linkTripService.setOverrideScheduleTime(false);
    
    _feedService = new FeedServiceImpl();
    _feedService.setAvlParseService(avlParseService);
    _feedService.setTuFeedBuilderServiceImpl(impl);
    avldp = new AVLDataParser(_feedService);


    linkTripService.setTimeToUpdateTripIds(Long.MAX_VALUE); //To prevent update
    TransitGraphDao _transitGraphDao = Mockito.mock(TransitGraphDao.class);

    LinkStopServiceImpl linkStopService = new LinkStopServiceImpl();
    if (legacyStopMapping) {
      linkStopService.setStopMapping(avldp.buildStopMapping(AVLDataParser.LEGACY_STOP_MAPPING_FILE));
    } else {
      linkStopService.setStopMapping(avldp.buildStopMapping(AVLDataParser.STOP_MAPPING_FILE));
    }
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

  // this is where the test actually occurs
  abstract public void testBuildScheduleFeedMessage() throws ClassNotFoundException, IOException;

  protected FeedEntity findByVehicleId(FeedMessage feedMessage, String search) {
    for (FeedEntity fe : feedMessage.getEntityList()) {
      if (search.equals(fe.getTripUpdate().getVehicle().getId())) {
        return fe;
      }
    }
    return null;
  }


  protected void buildRunBlocks(BlockRunServiceImpl blockRunService) {
    
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

}
