package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by ilan on 8/31/16.
 */
public class StationChainCacheHelper {

    private static final StationCacheInstance[] allStationInstances = initializeStationInstanceList();
    private static final StationChainCacheHelper instance = new StationChainCacheHelper();

    private StationChainCacheHelper() {

    }

    private static StationCacheInstance[] initializeStationInstanceList() {
        return (StationCacheInstance[]) Arrays.stream(PostgresqlExternalCache.DB_URLS)
                .map(PostgresqlExternalCache::new)
                .toArray();
    }

    public static StationChainCacheHelper getHelper() {
        return instance;
    }

    public List<TransStation> getCachedStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        return Arrays.stream(allStationInstances)
                .parallel()
                .map(cache -> cache.getCachedStations(center, range, startTime, maxDelta, chain))
                .filter(Objects::nonNull)
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
    }

    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {
        return Arrays.stream(allStationInstances)
                .parallel()
                .map(cache -> cache.cacheStations(center, range, startTime, maxDelta, stations))
                .anyMatch(Boolean::booleanValue);

    }
}
