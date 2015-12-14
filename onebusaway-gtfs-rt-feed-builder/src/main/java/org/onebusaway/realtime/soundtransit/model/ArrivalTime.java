package org.onebusaway.realtime.soundtransit.model;

import org.codehaus.jackson.annotate.JsonProperty;

public class ArrivalTime {
	@JsonProperty("Actual") private String actual;
	@JsonProperty("Scheduled") private String scheduled;
	@JsonProperty("Estimated") private String estimated;
	
	public String getActual() {
		return actual;
	}
	public void setActual(String actual) {
		this.actual = actual;
	}
	public String getScheduled() {
		return scheduled;
	}
	public void setScheduled(String scheduled) {
		this.scheduled = scheduled;
	}
	public String getEstimated() {
		return estimated;
	}
	public void setEstimated(String estimated) {
		this.estimated = estimated;
	}
}
