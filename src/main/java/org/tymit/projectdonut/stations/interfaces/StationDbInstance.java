package org.tymit.projectdonut.stations.interfaces;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();

    interface ComboDb extends StationDbInstance {

        List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain);

        default List<TransStation> queryStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
            return queryStations(center.getCoordinates(), range.inMiles(), startTime, maxDelta, chain);
        }
    }
}


