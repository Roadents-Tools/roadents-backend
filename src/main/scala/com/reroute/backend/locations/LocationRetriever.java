package com.reroute.backend.locations;

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

    /**
     * Gets all locations meeting within an area of a given type.
     *
     * @param center the center of the area
     * @param range  the distance around the center to check
     * @param type   the type of location to query for
     * @return the locations meeting this query
     */
    public static List<DestinationLocation> getLocations(LocationPoint center, Distance range, LocationType type) {
        List<DestinationLocation> locations = LocationProviderHelper.getHelper().getLocations(center, range, type);
        if (locations == null || locations.size() == 0) return new ArrayList<>(0);
        return locations;
    }

}
