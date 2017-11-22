package com.reroute.backend.locations;

import com.reroute.backend.locations.helpers.LocationCacheHelper;
import com.reroute.backend.locations.helpers.LocationProviderHelper;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.ArrayList;
import java.util.List;

/**
 * A series of static methods representing the API to query for destination locations.
 */
public class LocationRetriever {

    private static boolean isTest = false;

    /**
     * Gets all locations meeting within an area of a given type.
     *
     * @param center the center of the area
     * @param range  the distance around the center to check
     * @param type   the type of location to query for
     * @return the locations meeting this query
     */
    public static List<DestinationLocation> getLocations(LocationPoint center, Distance range, LocationType type) {
        List<DestinationLocation> locations = null;
        if (!isTest) locations = LocationCacheHelper.getHelper()
                .getCachedLocations(center, range, type);
        if (locations == null) {
            locations = LocationProviderHelper.getHelper().getLocations(center, range, type);
            if (!isTest) LocationCacheHelper.getHelper()
                    .cacheLocations(center, range, type, locations);
        }
        if (locations == null || locations.size() == 0) return new ArrayList<>(0);

        return locations;

    }

    /**
     * Sets whether or not the Location Retrieval infrastructure should act in a test context or not.
     * @param testMode whether or not the API is in a test
     */
    public static void setTestMode(boolean testMode) {
        isTest = testMode;
        LocationProviderHelper.setTestMode(testMode);
    }
}
