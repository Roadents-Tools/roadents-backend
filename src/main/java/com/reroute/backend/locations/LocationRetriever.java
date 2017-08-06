package com.reroute.backend.locations;

import com.reroute.backend.costs.CostCalculator;
import com.reroute.backend.costs.arguments.CostArgs;
import com.reroute.backend.locations.helpers.LocationCacheHelper;
import com.reroute.backend.locations.helpers.LocationProviderHelper;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationRetriever {

    private static boolean isTest = false;

    public static List<DestinationLocation> getLocations(LocationPoint center, Distance range, LocationType type, List<CostArgs> args) {
        List<DestinationLocation> locations = null;
        if (!isTest) locations = LocationCacheHelper.getHelper()
                .getCachedLocations(center, range, type);
        if (locations == null) {
            locations = LocationProviderHelper.getHelper().getLocations(center, range, type);
            if (!isTest) LocationCacheHelper.getHelper()
                    .cacheLocations(center, range, type, locations);
        }
        if (locations == null || locations.size() == 0) return new ArrayList<>(0);

        if (args == null || args.size() == 0) return locations;

        Iterator<DestinationLocation> locationsIterator = locations.iterator();
        while (locationsIterator.hasNext()) {
            for (CostArgs arg : args) {
                arg.setSubject(locationsIterator.next());
                if (!CostCalculator.isWithinCosts(arg)) locationsIterator.remove();
            }
        }
        return locations;
    }

    public static void setTestMode(boolean testMode) {
        isTest = testMode;
        LocationProviderHelper.setTestMode(testMode);
    }
}
