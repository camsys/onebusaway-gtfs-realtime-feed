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
package org.onebusaway.realtime.soundtransit.model;

import org.onebusaway.realtime.soundtransit.services.AvlParseService;

import java.util.Comparator;

/*
* This class will compare two StopUpdates based on their ArrivalTime
* information.  It first checks ActualTime, if any, then EstimatedTime,
* and finally ScheduledTime.
*/
public class StopUpdateComparator implements Comparator<StopUpdate> {

    private AvlParseService _service = null;
    public StopUpdateComparator(AvlParseService service) {
        _service = service;
    }

    @Override
    public int compare(StopUpdate su1, StopUpdate su2) {
        // Check that both updates have ArrivalTime objects
        ArrivalTime arrivalTime1 = su1.getArrivalTime();
        ArrivalTime arrivalTime2 = su2.getArrivalTime();
        long arrival1 = (arrivalTime1 != null) ? 1 : 0;
        long arrival2 = (arrivalTime2 != null) ? 1 : 0;
        if (arrival1 == 0 || arrival2 == 0) {
            return (int)(arrival1 - arrival2);
        }

        arrival1 = _service.parseAvlTimeAsMillis(arrivalTime1.getActual());
        arrival2 = _service.parseAvlTimeAsMillis(arrivalTime2.getActual());
        if (arrival1 > 0 && arrival2 > 0) {
            return (arrival1 > arrival2) ? 1 : 0;
        } else if (arrival1 != arrival2) {  // one is zero, the other isn't
            return (arrival1 > arrival2) ? 0 : 1;  // Non-zero has arrived already
        }

        arrival1 = _service.parseAvlTimeAsMillis(arrivalTime1.getEstimated());
        arrival2 = _service.parseAvlTimeAsMillis(arrivalTime2.getEstimated());
        if (arrival1 > 0 && arrival2 > 0) {
            return (arrival1 > arrival2) ? 1 : 0;
        } else if (arrival1 != arrival2) {
            return (arrival1 > arrival2) ? 0 : 1;
        }
        arrival1 = _service.parseAvlTimeAsMillis(arrivalTime1.getScheduled());
        arrival2 = _service.parseAvlTimeAsMillis(arrivalTime2.getScheduled());
        if (arrival1 > 0 && arrival2 > 0) {
            return (arrival1 > arrival2) ? 1 : 0;
        } else if (arrival1 != arrival2) {
            return (arrival1 > arrival2) ? 0 : 1;
        }

        return 0;
    }
}

