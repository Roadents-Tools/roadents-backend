package org.tymit.projectdonut.stations.interfaces;

import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();

    interface ComboDb extends StationDbInstance {

        List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain);

        default List<TransStation> queryStrippedStations(double[] center, double range, int limit) {
            if (center == null || range <= 0 || limit == 0) return Collections.emptyList();
            if (limit < 0) return queryStations(center, range, null, null, null);
            return queryStations(center, range, null, null, null).stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    interface DonutDb extends StationDbInstance {

        List<TransStation> getStationsInArea(LocationPoint center, double range);

        List<TransChain> getChainsForStation(TransStation station);

        List<TransStation> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta);
    }
}


