package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by ilan on 8/31/16.
 */
public class StationChainCacheHelper {

    private static final StationCacheInstance[] allStationInstances = initializeStationInstanceList();
    private static final ChainCacheInstance[] allChainInstances = initializeChainInstanceList();
    private static final StationChainCacheHelper instance = new StationChainCacheHelper();

    private StationChainCacheHelper() {

    }

    private static StationCacheInstance[] initializeStationInstanceList() {
        return new StationCacheInstance[] { new MemoryMapStationCache() };
    }

    private static ChainCacheInstance[] initializeChainInstanceList() {
        return new ChainCacheInstance[] { new MemoryMapChainCache() };
    }

    public static StationChainCacheHelper getHelper() {
        return instance;
    }

    public List<TransStation> getCachedStations(double[] center, double range, TransChain chain) {
        if (center == null && chain == null) return null;

        if (chain != null) {
            List<TransStation> cached = getCachedStations(chain);
            return (cached == null) ? null : cached.stream()
                    .filter(station -> center == null || LocationUtils.distanceBetween(center, station.getCoordinates(), true) < range)
                    .collect(Collectors.toList());
        }

        return getCachedStations(center, range);
    }

    private List<TransStation> getCachedStations(double[] center, double range) {
        if (center == null || range <= 0) return null;
        return Arrays.stream(allStationInstances)
                .map(instance -> instance.getCachedStations(center, range))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<TransStation> getCachedStations(TransChain chain) {
        if (chain == null) return null;
        return Arrays.stream(allChainInstances)
                .map(instance -> instance.getCachedStations(chain))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public void cacheStations(double[] center, double range, TransChain chain, List<TransStation> stations) {
        if (center != null && chain != null) return;
        if (chain != null) cacheStations(chain, stations);
        cacheStations(center, range, stations);
    }

    private void cacheStations(double[] center, double range, List<TransStation> stations) {
        allStationInstances[0].cacheStations(center, range, stations);
    }

    private void cacheStations(TransChain chain, List<TransStation> stations) {
        allChainInstances[0].cacheChain(chain, stations);
    }
}
