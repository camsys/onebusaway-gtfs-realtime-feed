package org.onebusaway.api.gtfs_realtime;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;

@Path("/trip-updates")
public class TripUpdatesResource extends GtfsRealtimeResource {

  private static final long serialVersionUID = 1L;
  
  public TripUpdatesResource() {
	  System.out.println("creating TripUpdatesResource");
  }

  @Path("test")
  @GET
  @Produces("text/plain")
  public String getTestMessage() {
	  //return _feedService.getCurrentTripUpdates();
	  return "Test Message";
  }
  
  @Path("debug")
  @GET
  @Produces("text/plain")
  public String getRealtimeTripUpdatesPBDebug() {
	  FeedMessage currentTripUpdates = _feedService.getCurrentTripUpdates();
	  String result = "";
	  if (currentTripUpdates == null) {
		  result = "currentTripUpdates is null";
	  } else {
		  result = currentTripUpdates.toString();
	  }
	  return result;
  }
  
  @Path("pb")
  @GET
  //@Produces("application/octet-stream")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] getRealtimeTripUpdatesPB() {
	  FeedMessage currentTripUpdates = _feedService.getCurrentTripUpdates();
	  return currentTripUpdates.toByteArray();
  }
  
  //@Override
  //protected FeedMessage fillFeedMessage() {
  //  return _feedService.getCurrentTripUpdates();
  //}

  /*
  @Override
  protected void fillFeedMessage(FeedMessage.Builder feed, String agencyId,
      long timestamp) {

    ListBean<VehicleStatusBean> vehicles = _service.getAllVehiclesForAgency(
        agencyId, timestamp);

    for (VehicleStatusBean vehicle : vehicles.getList()) {
      TripStatusBean tripStatus = vehicle.getTripStatus();
      if (tripStatus == null) {
        continue;
      }
      TripBean activeTrip = tripStatus.getActiveTrip();
      RouteBean route = activeTrip.getRoute();

      FeedEntity.Builder entity = feed.addEntityBuilder();
      entity.setId(Integer.toString(feed.getEntityCount()));
      TripUpdate.Builder tripUpdate = entity.getTripUpdateBuilder();

      TripDescriptor.Builder tripDesc = tripUpdate.getTripBuilder();
      tripDesc.setTripId(normalizeId(activeTrip.getId()));
      tripDesc.setRouteId(normalizeId(route.getId()));

      VehicleDescriptor.Builder vehicleDesc = tripUpdate.getVehicleBuilder();
      vehicleDesc.setId(normalizeId(vehicle.getVehicleId()));

      StopBean nextStop = tripStatus.getNextStop();
      if (nextStop != null) {
        TripUpdate.StopTimeUpdate.Builder stopTimeUpdate = tripUpdate.addStopTimeUpdateBuilder();
        stopTimeUpdate.setStopId(normalizeId(nextStop.getId()));
        TripUpdate.StopTimeEvent.Builder departure = stopTimeUpdate.getDepartureBuilder();
        departure.setTime(timestamp / 1000 + tripStatus.getNextStopTimeOffset());
      }

      tripUpdate.setTimestamp(vehicle.getLastUpdateTime() / 1000);
    }
  }
  */
}
