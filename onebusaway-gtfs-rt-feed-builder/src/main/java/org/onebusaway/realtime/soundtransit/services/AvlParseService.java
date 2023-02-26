/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
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
package org.onebusaway.realtime.soundtransit.services;

import java.util.Date;

import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.TripInfo;

public interface AvlParseService {
  LinkAVLData parseAVLFeed(String dataFromAvl);
  Date parseAvlTime(String arrivalTime);
  long parseAvlTimeAsMillis(String arrivalTime);
  long parseAvlTimeAsSeconds(String arrivalTime);
  String formatAvlTime(Date d);
  String hashVehicleId(String vehicleId);
  String getLinkRouteId();
  boolean hasPredictions(TripInfo trip);
}
