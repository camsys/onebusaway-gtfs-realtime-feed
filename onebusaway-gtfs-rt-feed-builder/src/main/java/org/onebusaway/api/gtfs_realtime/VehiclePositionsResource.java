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
	  //return _feedService.getCurrentTripUpdates();
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
	  return currentVehiclePositions.toByteArray();
  }
  
  //@Override
  //protected FeedMessage fillFeedMessage() {
  //  return _feedService.getCurrentVehiclePositions();
  //}

  
  /*
  @Override
  protected void fillFeedMessage(FeedMessage.Builder feed, String agencyId,
      long timestamp) {

    ListBean<VehicleStatusBean> vehicles = _service.getAllVehiclesForAgency(
        agencyId, timestamp);

    for (VehicleStatusBean vehicle : vehicles.getList()) {
      FeedEntity.Builder entity = feed.addEntityBuilder();
      entity.setId(Integer.toString(feed.getEntityCount()));
      VehiclePosition.Builder vehiclePosition = entity.getVehicleBuilder();

      TripStatusBean tripStatus = vehicle.getTripStatus();
      if (tripStatus != null) {
        TripBean activeTrip = tripStatus.getActiveTrip();
        RouteBean route = activeTrip.getRoute();

        TripDescriptor.Builder tripDesc = vehiclePosition.getTripBuilder();
        tripDesc.setTripId(normalizeId(activeTrip.getId()));
        tripDesc.setRouteId(normalizeId(route.getId()));
      }

      VehicleDescriptor.Builder vehicleDesc = vehiclePosition.getVehicleBuilder();
      vehicleDesc.setId(normalizeId(vehicle.getVehicleId()));

      CoordinatePoint location = vehicle.getLocation();
      if (location != null) {
        Position.Builder position = vehiclePosition.getPositionBuilder();
        position.setLatitude((float) location.getLat());
        position.setLongitude((float) location.getLon());
      }

  vehiclePosition.setTimestamp(vehicle.getLastUpdateTime() / 1000);
}
}
*/
}
