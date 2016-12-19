package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 8/31/16.
 */
public class MemoryMapStationCache implements StationCacheInstance {

    private static final long ENTRY_EXPIRE_TIME = 15L * 60L * 1000L;
    private static final int MAX_TOTAL_SIZE = 100000;
    private static final double ERROR_MARGIN = .000001;


    private final Map<String, MapEntry> cache = new ConcurrentHashMap<>();
    private int size = 0;

    @Override
    public void cacheStations(double[] center, double range, List<TransStation> stations) {
        if (stations == null || stations.size() == 0 || range <= 0) return;
        MapEntry old = cache.get(getTag(center));
        if (old != null && old.range > range) return;
        MapEntry newEntry = new MapEntry(stations, center, range);
        cache.put(getTag(center), newEntry);
        size -= (old != null) ? old.stations.size() : 0;
        size += stations.size();
        trimToSize();
    }

    @Override
    public List<TransStation> getCachedStations(double[] center, double range) {
        trimExpired();
        MapEntry existing = cache.get(getTag(center));
        if (existing == null || existing.range < range) return null;
        if (Math.abs(range - existing.range) < ERROR_MARGIN) return existing.stations;
        List<TransStation> rval = existing.stations.parallelStream()
                .filter(station -> LocationUtils.distanceBetween(station.getCoordinates(), center, true) < range)
                .collect(Collectors.toList());
        if (rval.size() == 0) return null;
        return rval;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private void trimExpired() {
        Set<String> keyClone = new HashSet<>(cache.keySet());
        keyClone.stream()
                .filter(key -> cache.get(key) != null && cache.get(key).createdTime < System.currentTimeMillis() - ENTRY_EXPIRE_TIME)
                .forEach(cache::remove);
    }

    private void trimToSize() {
        if (size <= MAX_TOTAL_SIZE) return;
        List<String> keyClone = new ArrayList<>(cache.keySet());
        Collections.sort(keyClone, (o1, o2) -> {
            long o1time = cache.get(o1).createdTime;
            long o2time = cache.get(o2).createdTime;
            if (o1time == o2time) return 0;
            return (o1time > o2time) ? -1 : 1;
        });

        while (size > MAX_TOTAL_SIZE) {
            MapEntry oldest = cache.remove(keyClone.remove(0));
            size -= oldest.stations.size();
        }

    }

    private static String getTag(double[] center) {
        return center[0] + "," + center[1];
    }


    private static class MapEntry {
        public final List<TransStation> stations;
        public final double[] center;
        public final double range;
        public final long createdTime;

        public MapEntry(List<TransStation> stations, double[] center, double range) {
            this.stations = stations;
            this.center = center;
            this.range = range;
            this.createdTime = System.currentTimeMillis();
        }
    }
}
