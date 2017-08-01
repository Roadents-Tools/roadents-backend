package org.tymit.projectdonut.locations.memory;

import org.tymit.projectdonut.locations.interfaces.LocationCacheInstance;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.distance.DistanceUnits;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public class MemoryMapLocationCache implements LocationCacheInstance {

    private static final double ERROR_MARGIN = .001;

    private final Map<String, List<DestinationLocation>> cache;
    private final Map<String, Distance> ranges;

    public MemoryMapLocationCache() {
        this.cache = new ConcurrentHashMap<>();
        this.ranges = new ConcurrentHashMap<>();
    }

    @Override
    public void cacheLocations(LocationPoint center, Distance range, LocationType type, List<DestinationLocation> locations) {
        if (center == null || range.inMeters() == 0 || type == null || locations == null) return; //Arg check
        String tag = generateTag(center, type);
        if (ranges.getOrDefault(tag, new Distance(0, DistanceUnits.METERS)).inMeters() >= range.inMeters())
            return; //Already cached
        locations = new ArrayList<>(locations); //To not modify the old list
        cache.put(tag, locations);
        ranges.put(tag, range);
    }

    @Override
    public List<DestinationLocation> getCachedLocations(LocationPoint center, Distance range, LocationType type) {
        String tag = generateTag(center, type);
        Distance cachedRange = ranges.getOrDefault(tag, new Distance(0, DistanceUnits.METERS));
        if (cachedRange.inMeters() < range.inMeters()) return null;
        if (Math.abs(cachedRange.inMeters() - range.inMeters()) < ERROR_MARGIN) return cache.get(tag);
        return cache.get(tag).stream()
                .filter(toCheck -> LocationUtils.distanceBetween(toCheck, center)
                        .inMeters() <= range.inMeters() + ERROR_MARGIN)
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

    @Override
    public void close() {
        clear();
    }

    private static String generateTag(LocationPoint center, LocationType type) {
        return center.getCoordinates()[0] + type.getEncodedname() + center.getCoordinates()[1];
    }
}
