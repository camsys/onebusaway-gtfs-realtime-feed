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

