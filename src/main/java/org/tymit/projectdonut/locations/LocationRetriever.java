package org.tymit.projectdonut.locations;

import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.costs.arguments.CostArgs;
import org.tymit.projectdonut.locations.helpers.LocationCacheHelper;
import org.tymit.projectdonut.locations.helpers.LocationProviderHelper;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;

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
                .getCachedLocations(center.getCoordinates(), range.inMiles(), type);
        if (locations == null) {
            locations = LocationProviderHelper.getHelper().getLocations(center, range, type);
            if (!isTest) LocationCacheHelper.getHelper()
                    .cacheLocations(center.getCoordinates(), range.inMiles(), type, locations);
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
