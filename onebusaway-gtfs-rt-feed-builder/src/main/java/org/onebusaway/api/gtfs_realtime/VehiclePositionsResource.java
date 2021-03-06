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
package org.onebusaway.api.gtfs_realtime;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

@Path("/vehicle-positions")
public class VehiclePositionsResource extends GtfsRealtimeResource {

  private static final long serialVersionUID = 1L;

  @Path("test")
  @GET
  @Produces("text/plain")
  public String getTestMessage() {
	  return "Test Message from vehicle-positions feed";
  }
  
  @Path("debug")
  @GET
  @Produces("text/plain")
  public String getRealtimeVehiclePositionsPBDebug() {
	  FeedMessage currentVehiclePositions = _feedService.getCurrentVehiclePositions();
	  String result = "";
	  if (currentVehiclePositions == null) {
		  result = "currentVehiclePositions is null";
	  } else {
		  result = currentVehiclePositions.toString();
	  }
	  return result;
  }
  
  @Path("pb")
  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] getRealtimeVehiclePositionsPB() {
	  FeedMessage currentVehiclePositions = _feedService.getCurrentVehiclePositions();
	  if (currentVehiclePositions != null) {
	    return currentVehiclePositions.toByteArray();
	  } else {
      return new byte[0];
    }

  }
}
