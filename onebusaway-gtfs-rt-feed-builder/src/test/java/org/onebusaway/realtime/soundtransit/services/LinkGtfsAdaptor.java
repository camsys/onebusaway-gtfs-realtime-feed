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
package org.onebusaway.realtime.soundtransit.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
    Collections.sort(stopTimes);
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
    Block b = filterActiveBlock(blocks, serviceDate);
    if (b == null) {
      fail("no block present for run=" + blockRunNumber);
      return null;
    }
    return b;
  }

  public Block getBlockBySequence(int sequence) {
    for (Block block : dao.getAllBlocks()) {
      if (block.getBlockSequence() == sequence)
      return block;
    }
    return null;
  }
  
  public ServiceCalendar getCalendarByServiceId(String serviceId) {
    for (ServiceCalendar calendar : dao.getAllCalendars()) {
      if (serviceId.equals(calendar.getServiceId().getId())) {
        return calendar;
      }
    }
    return null;
  }
  
  Block filterActiveBlock(List<Block> blocks, ServiceDate serviceDate) {
    for (Block block : blocks) {
      Trip trip = getTripByBlockId("" + block.getBlockSequence());
      if (isActiveServiceId(trip, serviceDate)) {
        return block;
      }
    }
    return null; // not found
  }

  boolean isActiveServiceId(Trip trip, ServiceDate serviceDate) {
    ServiceCalendar calendar = getCalendarByServiceId(trip.getServiceId().getId());
    long cStart = calendar.getStartDate().getAsDate().getTime();
    long cEnd = calendar.getEndDate().getAsDate().getTime();
    long sd = serviceDate.getAsDate().getTime();
    if (sd >= cStart && sd <= cEnd) {
      // we are in the correct range, need to verify the day
      if (isDayActive(calendar, serviceDate)) {
        return true;
      }
    }
    return false;
  }

  boolean isDayActive(ServiceCalendar calendar,
          ServiceDate serviceDate) {
    Calendar asCalendar = serviceDate.getAsCalendar(TimeZone.getDefault());
    switch (asCalendar.get(Calendar.DAY_OF_WEEK)) {
      case Calendar.MONDAY:
        if (calendar.getMonday() > 0) return true;
        break;
      case Calendar.TUESDAY:
        if (calendar.getTuesday() > 0) return true;
        break;
      case Calendar.WEDNESDAY:
        if (calendar.getWednesday() > 0)  return true;
        break;
      case Calendar.THURSDAY:
        if (calendar.getThursday() > 0) return true;
        break;
      case Calendar.FRIDAY:
        if (calendar.getFriday() > 0) return true;
        break;
      case Calendar.SATURDAY:
        if (calendar.getSaturday() > 0) return true;
        break;
      case Calendar.SUNDAY:
        if (calendar.getSunday() > 0) return true;
        break;
      default:
        fail("unexpected case for asCalendar=" + asCalendar
            + " for day=" + asCalendar.get(Calendar.DAY_OF_WEEK));
        return false;
    }
    return false;
  } 

  
  public AgencyAndId findBestTrip(String blockId, Long scheduleTime, ServiceDate serviceDate) {
    for (Trip trip : dao.getAllTrips()) {
      if (trip.getBlockId() != null && trip.getBlockId().equals(blockId)) {
        if (this.isActiveServiceId(trip, serviceDate)) {
          List<StopTime> stopTimes = getStopTimesForTripId(trip.getId().getId());
          verifyStopTimes(stopTimes);
          Long firstStopTime = stopTimes.get(0).getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
          Long lastStopTime = stopTimes.get(stopTimes.size()-1).getArrivalTime() * 1000 + serviceDate.getAsDate().getTime();
          assertTrue(firstStopTime < lastStopTime);
          Long window = 5 * 60 * 1000l; // no overlap
          if (scheduleTime >= firstStopTime - window && scheduleTime <= lastStopTime + window) {
            return trip.getId();
          } else {
            _log.debug("no trip found for " + blockId + " on serviceDate=" + serviceDate
                + " with " + new Date(firstStopTime) + " <= " + new Date(scheduleTime) + " <= " + new Date(lastStopTime));
          }
        }
      }
    }
    
    return null;
  }

  private void verifyStopTimes(List<StopTime> stopTimes) {
    int min = Integer.MIN_VALUE;
    for (StopTime st : stopTimes) {
      if (st.getArrivalTime() > min) {
        min = st.getArrivalTime();
      } else {
        fail("stop times not in increasing order, last=" + min
            + ", current=" + st.getArrivalTime());
      }
    }
  }

  long parseTime(String dateStr) throws Exception {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.parse(dateStr).getTime();
  }

}
