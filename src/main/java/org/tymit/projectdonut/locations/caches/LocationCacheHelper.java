package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationCacheHelper {

    private static final LocationCacheInstance[] allInstances = initializeCacheInstanceList();
    private static final LocationCacheHelper instance = new LocationCacheHelper();

    private LocationCacheHelper() {

    }

    private static LocationCacheInstance[] initializeCacheInstanceList() {
        return new LocationCacheInstance[]{new MemoryMapLocationCache()};
    }

    public static LocationCacheHelper getHelper() {
        return instance;
    }

    public List<DestinationLocation> getCachedLocations(double[] center, double range, LocationType type) {
        return Arrays.stream(allInstances)
                .map(instance -> instance.getCachedLocations(center, range, type))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public void cacheLocations(double[] center, double range, LocationType type, List<DestinationLocation> locations) {
        allInstances[0].cacheLocations(center, range, type, locations);
    }
}
