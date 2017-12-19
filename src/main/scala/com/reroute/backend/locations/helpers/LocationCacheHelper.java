package com.reroute.backend.locations.helpers;

import com.reroute.backend.locations.interfaces.LocationCacheInstance;
import com.reroute.backend.locations.memory.MemoryMapLocationCache;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The delegater and aggregator of all LocationCaches.
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

    /**
     * Gets the instance of the delegater.
     *
     * @return the current helper instance
     */
    public static LocationCacheHelper getHelper() {
        return instance;
    }

    /**
     * Runs a query against the current LocationCaches.
     * @param center the center of the query
     * @param range the distance around the center to query
     * @param type the type of location to query
     * @return the locations meeting the query
     */
    public List<DestinationLocation> getCachedLocations(LocationPoint center, Distance range, LocationType type) {
        return Arrays.stream(allInstances)
                .map(instance -> instance.getCachedLocations(center, range, type))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Caches a query against within current LocationCaches.
     * @param center the center of the query
     * @param range the distance around the center to query
     * @param type the type of location to query
     * @param locations the locations meeting this query
     */
    public void cacheLocations(LocationPoint center, Distance range, LocationType type, List<DestinationLocation> locations) {
        allInstances[0].cacheLocations(center, range, type, locations);
    }

    /**
     * Closes all connected LocationCaches.
     */
    public void closeAllCaches() {
        for (LocationCacheInstance instance : allInstances) {
            instance.close();
        }
    }
}
