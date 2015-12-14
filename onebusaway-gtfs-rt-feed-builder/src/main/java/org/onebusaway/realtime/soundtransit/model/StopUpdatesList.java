package org.onebusaway.realtime.soundtransit.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

public class StopUpdatesList {
	@JsonProperty("Update") List<StopUpdate> updates;

	public List<StopUpdate> getUpdates() {
		return updates;
	}
	public void setUpdates(List<StopUpdate> updates) {
		this.updates = updates;
	}
}

