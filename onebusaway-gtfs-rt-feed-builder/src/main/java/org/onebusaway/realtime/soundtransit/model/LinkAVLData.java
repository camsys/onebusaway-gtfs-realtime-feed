package org.onebusaway.realtime.soundtransit.model;

import java.sql.Date;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

public class LinkAVLData {
	@JsonProperty("Trips") private TripInfoList trips;

	public TripInfoList getTrips() {
		return trips;
	}
	public void setTrips(TripInfoList trips) {
		this.trips = trips;
	}	
}
