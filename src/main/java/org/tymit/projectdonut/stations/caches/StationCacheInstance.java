package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {
    boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations);


    List<TransStation> getCachedStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain);
}
