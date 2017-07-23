package org.tymit.projectdonut.stations.interfaces;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.List;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {

    default boolean cacheStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {
        return cacheStations(center.getCoordinates(), range.inMiles(), startTime, maxDelta, stations);
    }

    @Deprecated
    boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations);

    default List<TransStation> getCachedStations(LocationPoint center, Distance range, TimePoint start, TimeDelta maxDelta, TransChain chain) {
        return getCachedStations(center.getCoordinates(), range.inMiles(), start, maxDelta, chain);
    }

    @Deprecated
    List<TransStation> getCachedStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain);

    void close();
}
