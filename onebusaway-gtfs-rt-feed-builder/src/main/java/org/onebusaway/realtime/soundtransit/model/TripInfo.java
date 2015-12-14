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
package org.onebusaway.realtime.soundtransit.model;

import org.codehaus.jackson.annotate.JsonProperty;

public class TripInfo {
	@JsonProperty("TripId") String tripId;
	@JsonProperty("LastUpdatedDate") String lastUpdatedDate;
	@JsonProperty("VehicleId") String vehicleId;
	@JsonProperty("LastStopName") String lastStopName;
	@JsonProperty("LastStopId") String lastStopId;
	@JsonProperty("Lat") String lat;
	@JsonProperty("Lon") String lon;
	@JsonProperty("Direction") String direction;
	@JsonProperty("StopUpdates") StopUpdatesList stopUpdates;
	
	public String getTripId() {
		return tripId;
	}
	public void setTripId(String tripId) {
		this.tripId = tripId;
	}
	public String getLastUpdatedDate() {
		return lastUpdatedDate;
	}
	public void setLastUpdatedDate(String lastUpdatedDate) {
		this.lastUpdatedDate = lastUpdatedDate;
	}
	public String getVehicleId() {
		return vehicleId;
	}
	public void setVehicleId(String vehicleId) {
		this.vehicleId = vehicleId;
	}
	public String getLastStopName() {
		return lastStopName;
	}
	public void setLastStopName(String lastStopName) {
		this.lastStopName = lastStopName;
	}
	public String getLastStopId() {
		return lastStopId;
	}
	public void setLastStopId(String lastStopId) {
		this.lastStopId = lastStopId;
	}
	public String getLat() {
		return lat;
	}
	public void setLat(String lat) {
		this.lat = lat;
	}
	public String getLon() {
		return lon;
	}
	public void setLon(String lon) {
		this.lon = lon;
	}
	public String getDirection() {
		return direction;
	}
	public void setDirection(String direction) {
		this.direction = direction;
	}
	public StopUpdatesList getStopUpdates() {
		return stopUpdates;
	}
	public void setStopUpdates(StopUpdatesList stopUpdates) {
		this.stopUpdates = stopUpdates;
	}
}
