package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationCacheHelper {

    private static final LocationCacheInstance[] allInstances = initializeCacheInstanceList();
    private static LocationCacheHelper instance = new LocationCacheHelper();

    private LocationCacheHelper() {

    }

    private static LocationCacheInstance[] initializeCacheInstanceList() {
        return new LocationCacheInstance[0];
    }

    public static LocationCacheHelper getHelper() {
        return instance;
    }

    public List<LocationPoint> getCachedLocations(double[] center, double range, LocationType type) {
        for (LocationCacheInstance instance : allInstances) {
            List<LocationPoint> cached = instance.getCachedLocations(center, range, type);
            if (cached != null) return cached;
        }
        return null;
    }
}
