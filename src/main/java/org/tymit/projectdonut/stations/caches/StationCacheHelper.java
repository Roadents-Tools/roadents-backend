package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 8/31/16.
 */
public class StationCacheHelper {

    private static final StationCacheInstance[] allInstances = initializeCacheInstanceList();
    private static StationCacheHelper instance = new StationCacheHelper();

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

        for (StationCacheInstance instance : allInstances) {
            List<TransStation> cached = instance.getCachedStations(center, range);
            if (cached != null) return cached;
        }
        return null;
    }

    public void cacheStations(double[] center, double range, List<TransStation> stations) {
        allInstances[0].cacheStations(center, range, stations);
    }
}
