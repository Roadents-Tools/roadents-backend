package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransStation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Created by ilan on 8/31/16.
 */
public class StationCacheHelper {

    private static final StationCacheInstance[] allInstances = initializeCacheInstanceList();
    private static final StationCacheHelper instance = new StationCacheHelper();

    private StationCacheHelper() {

    }

    private static StationCacheInstance[] initializeCacheInstanceList() {
        return new StationCacheInstance[] { new MemoryMapStationCache() };
    }

    public static StationCacheHelper getHelper() {
        return instance;
    }

    public List<TransStation> getCachedStations(double[] center, double range) {

        if (center == null || range <= 0) return null;

        return Arrays.stream(allInstances)
                .map(instance -> instance.getCachedStations(center, range))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public void cacheStations(double[] center, double range, List<TransStation> stations) {
        allInstances[0].cacheStations(center, range, stations);
    }
}
