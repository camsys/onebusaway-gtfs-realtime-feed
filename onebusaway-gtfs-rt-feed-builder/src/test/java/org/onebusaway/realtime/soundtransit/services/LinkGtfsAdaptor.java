package org.onebusaway.realtime.soundtransit.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Block;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block/run/trip mapping using raw GTFS that TUFeeBuilder uses the bundle for. 
 *
 */
public class LinkGtfsAdaptor {

  private static final Logger _log = LoggerFactory.getLogger(LinkGtfsAdaptor.class);
  private GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
  private ServiceDate serviceDate;
  
  public LinkGtfsAdaptor(String bundleDir, String serviceDateStr) throws Exception {
    serviceDate = ServiceDate.parseString(serviceDateStr);
    
    String gtfs = getClass().getResource(bundleDir).getFile();
    GtfsReader reader = new GtfsReader();
    
    reader.setEntityStore(dao);
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
    } catch (IOException e) {
      fail("exception loading GTFS:" + e);
    }
  }
  
  public ServiceDate getServiceDate() {
    return serviceDate;
  }
  
  public Collection<Block> getAllBlocks() {
    return dao.getAllBlocks();
  }
  
  public Trip getTripById(String tripId) {
    for (Trip trip : dao.getAllTrips()) {
      if (trip.getId() != null && trip.getId().getId().equals(tripId)) {
        return trip;
      }
    }
    fail("missing trip for tripId=" + tripId);
    return null;
  }

  public Trip getTripByBlockId(String blockId) {
    for (Trip trip : dao.getAllTrips()) {
      if (trip.getBlockId() != null && trip.getBlockId().equals(blockId)) {
        return trip;
      }
    }
    fail("missing trip for blockId=" + blockId);
    return null;
  }

  public List<StopTime> getStopTimesForTripId(String tripId) {
    List<StopTime> stopTimes = new ArrayList<StopTime>();
    for (StopTime st : dao.getAllStopTimes()) {
      if (st.getTrip().getId().getId().equals(tripId)) {
        stopTimes.add(st);
      }
    }
    assertTrue(stopTimes.size() > 0);
    return stopTimes;
  }
  
  public String getTripDirectionFromTripId(String tripId) {
    Trip trip = getTripById(tripId);
    return trip.getDirectionId();
  }


  public Block getBlockForRun(String blockRunNumber, ServiceDate serviceDate) {
    List<Block> blocks = new ArrayList<Block>();
    int blockRun = Integer.parseInt(blockRunNumber);
    for (Block block : dao.getAllBlocks()) {
      if (block.getBlockRun() == blockRun && block.getBlockRoute() == 599) {
        blocks.add(block);
      }
    }
    if (blocks.size() == 3) {
      return filterActiveBlock(blocks, serviceDate);
    }
    if (blocks.size() == 1) {
      return blocks.get(0); // in service only
    }
    
    fail("unexpected number of blocks=" + blocks.size());
    return null;
  }

  
  private Block filterActiveBlock(List<Block> blocks, ServiceDate serviceDate) {
    for (Block block : blocks) {
      Trip trip = getTripByBlockId("" + block.getBlockSequence());
      if (isActiveServiceId(trip, serviceDate)) {
        return block;
      }
    }
    return null; // not found
  }

  private boolean isActiveServiceId(Trip trip, ServiceDate serviceDate) {
    for (ServiceCalendar calendar : dao.getAllCalendars()) {
      if (calendar.getServiceId().equals(trip.getServiceId())) {
            if (calendar.getStartDate().compareTo(serviceDate) <= 0
            && calendar.getEndDate().compareTo(serviceDate) >= 0) {
          // we are in the correct range, need to verify the day
          if (isDayActive(calendar, serviceDate)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean isDayActive(ServiceCalendar calendar,
          ServiceDate serviceDate) {
    Calendar asCalendar = serviceDate.getAsCalendar(TimeZone.getDefault());
    switch (asCalendar.get(Calendar.DAY_OF_WEEK)) {
      case Calendar.MONDAY:
        if (calendar.getMonday() > 0) return true;
      case Calendar.TUESDAY:
        if (calendar.getTuesday() > 0) return true;
      case Calendar.WEDNESDAY:
        if (calendar.getWednesday() > 0)  return true;
      case Calendar.THURSDAY:
        if (calendar.getThursday() > 0) return true;
      case Calendar.FRIDAY:
        if (calendar.getFriday() > 0) return true;
      case Calendar.SATURDAY:
        if (calendar.getSaturday() > 0) return true;
      case Calendar.SUNDAY:
        if (calendar.getSunday() > 0) return true;
      default:
        return false;
    }
    
  } 

  
  public AgencyAndId findBestTrip(String blockId, Long scheduleTime, ServiceDate serviceDate) {
    Trip bestTrip = null;
    int i = 0;
    for (Trip trip : dao.getAllTrips()) {
      i++;
      if (trip.getBlockId() != null && trip.getBlockId().equals(blockId)
          && this.isActiveServiceId(trip, serviceDate)) {
        bestTrip = trip;
      }
    }
    if (bestTrip == null) {
      throw new IllegalStateException("blockId " + blockId + " does not exist in GTFS with " + i + " trips");
    }
    List<StopTime> stopTimes = getStopTimesForTripId(bestTrip.getId().getId());
    if (!stopTimes.isEmpty()) {
      return bestTrip.getId();
    }
    
    throw new IllegalStateException("no stop times for trip=" + bestTrip);
  }

  long parseTime(String dateStr) throws Exception {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.parse(dateStr).getTime();
  }

}
