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

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

public class StopUpdate {
	@JsonProperty("StopId") String stopId;
	@JsonProperty("StationName") String stationName;
	@JsonProperty("Frequency") String frequency;
	@JsonProperty("ArrivalTime") ArrivalTime arrivalTime;
	
	public String getStopId() {
		return stopId;
	}
	public void setStopId(String stopId) {
		this.stopId = stopId;
	}
	public String getStationName() {
		return stationName;
	}
	public void setStationName(String stationName) {
		this.stationName = stationName;
	}
	public String getFrequency() {
		return frequency;
	}
	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}
	public ArrivalTime getArrivalTime() {
		return arrivalTime;
	}
	public void setArrivalTime(ArrivalTime arrivalTime) {
		this.arrivalTime = arrivalTime;
	}
	public String toString() {
	  return "{Stop " + stopId + " : "
	      + (arrivalTime==null?"null arrivalTime":arrivalTime.toString())
	      + "}";
	}
}
