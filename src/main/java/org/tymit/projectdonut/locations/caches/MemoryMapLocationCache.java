package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ilan on 7/7/16.
 */
public class MemoryMapLocationCache implements LocationCacheInstance {

    private static final double ERROR_MARGIN = .000001;

    private final Map<String, List<DestinationLocation>> cache;
    private final Map<String, Double> ranges;

    public MemoryMapLocationCache() {
        this.cache = new ConcurrentHashMap<>();
        this.ranges = new ConcurrentHashMap<>();
    }

    @Override
    public void cacheLocations(double[] center, double range, LocationType type, List<DestinationLocation> locations) {
        if (center == null || range == 0 || type == null || locations == null) return; //Arg check
        String tag = generateTag(center, type);
        if (ranges.getOrDefault(tag, 0.0) >= range) return; //Already cached
        locations = new ArrayList<>(locations); //To not modify the old list
        cache.put(tag, locations);
        ranges.put(tag, range);
    }

    @Override
    public List<DestinationLocation> getCachedLocations(double[] center, double range, LocationType type) {
        String tag = generateTag(center, type);
        double cachedRange = ranges.getOrDefault(tag, 0.0);
        if (cachedRange < range) return null;
        if (Math.abs(cachedRange - range) < ERROR_MARGIN) return cache.get(tag);
        int cacheSize = cache.get(tag).size();
        return IntStream.range(0, cacheSize)
                .mapToObj(i -> cache.get(tag).get(i))
                .filter(toCheck -> LocationUtils.distanceBetween(toCheck.getCoordinates(), center, true) <= range + ERROR_MARGIN)
                .collect(Collectors.toList());
    }

    @Override
    public int getSize() {
        return cache.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    @Override
    public void clear() {
        cache.clear();
        ranges.clear();
    }

    private static String generateTag(double[] center, LocationType type) {
        return center[0] + type.getEncodedname() + center[1];
    }
}
