package org.onebusaway.realtime.soundtransit.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

public class TripInfoList {
	@JsonProperty("Trip") private List<TripInfo> trips;

	public List<TripInfo> getTrips() {
		return trips;
	}
	public void setTrips(List<TripInfo> trips) {
		this.trips = trips;
	}
}
