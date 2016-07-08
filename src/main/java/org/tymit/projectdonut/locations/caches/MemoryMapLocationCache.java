package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/7/16.
 */
public class MemoryMapLocationCache implements LocationCacheInstance {

    private static final double ERROR_MARGIN = .000001;

    private Map<String, List<DestinationLocation>> cache;
    private Map<String, Double> ranges;

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

        //We sort the locations by distance to the center so that we can retrieve them quicker.
        locations.sort((destinationLocation, t1) ->
                (int) Math.round(LocationUtils.distanceBetween(t1.getCoordinates(), center) - LocationUtils.distanceBetween(destinationLocation.getCoordinates(), center))
        );

        cache.put(tag, locations);
        ranges.put(tag, range);
    }

    @Override
    public List<DestinationLocation> getCachedLocations(double[] center, double range, LocationType type) {
        String tag = generateTag(center, type);
        double cachedRange = ranges.getOrDefault(tag, 0.0);
        if (cachedRange < range) return null;
        if (Math.abs(cachedRange - range) < ERROR_MARGIN) return cache.get(tag);
        List<DestinationLocation> toReturn = new ArrayList<>();
        int cacheSize = cache.get(tag).size();
        for (int i = 0; i < cacheSize; i++) {
            DestinationLocation toCheck = cache.get(tag).get(i);
            if (LocationUtils.distanceBetween(toCheck.getCoordinates(), center) > range + ERROR_MARGIN) break;
            toReturn.add(toCheck);
        }
        return toReturn;
    }

    @Override
    public int getSize() {
        int size = 0;
        for (String tag : cache.keySet()) size += cache.get(tag).size();
        return size;
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
