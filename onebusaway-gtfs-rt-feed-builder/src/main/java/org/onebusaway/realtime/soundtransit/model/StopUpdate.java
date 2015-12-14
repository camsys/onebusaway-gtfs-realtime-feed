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
}
