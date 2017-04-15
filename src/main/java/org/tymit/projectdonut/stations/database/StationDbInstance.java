package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();

    interface AreaDb extends StationDbInstance {
        List<TransStation> queryStations(double[] center, double range, TransChain chain);
    }

    interface ScheduleDb extends StationDbInstance {
        List<TransStation> queryStations(TimePoint startTime, TimeDelta maxDelta, TransChain chain);
    }

    interface ComboDb extends ScheduleDb, AreaDb {
        @Override
        default List<TransStation> queryStations(double[] center, double range, TransChain chain) {
            return queryStations(center, range, null, null, chain);
        }

        List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain);

        @Override
        default List<TransStation> queryStations(TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
            return queryStations(null, -1, startTime, maxDelta, chain);
        }
    }
}


