/**
 * Copyright (C) 2016 Brian Ferris <bdferris@onebusaway.org>
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

import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;

public class StopOffset {
  private String gtfsStopId;
  private String linkStopId;
  private String direction;
  private int offset;
  
  public String getGtfsStopId() {
    return gtfsStopId;
  }
  public void setGtfsStopId(String gtfsStopId) {
    this.gtfsStopId = gtfsStopId;
  }
  public String getLinkStopId() {
    return linkStopId;
  }
  public void setLinkStopId(String linkStopId) {
    this.linkStopId = linkStopId;
  }
  public String getDirection() {
    return direction;
  }
  public void setDirection(String direction) {
    this.direction = direction;
  }
  public int getOffset() {
    return offset;
  }
  public void setOffset(int offset) {
    this.offset = offset;
  }
  
  public StopOffset(String gtfsStopId, String linkStopId, String direction, int offset) {
    this.gtfsStopId = gtfsStopId;
    this.linkStopId = linkStopId;
    this.direction = direction;
    this.offset = offset;
  }
  
  public String toString() {
    return ("\nGTFS StopId: " + gtfsStopId + ", AVL StopId: " + linkStopId
        + ", direction: " + direction + ", offset: " + offset);
  }
}
