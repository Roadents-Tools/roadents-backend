package com.reroute.backend.locations.helpers;

import com.reroute.backend.locations.foursquare.FoursquareLocationsProvider;
import com.reroute.backend.locations.interfaces.LocationProvider;
import com.reroute.backend.locations.test.TestLocationProvider;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationProviderHelper {

    private static boolean isTest = false;
    private static LocationProviderHelper instance = new LocationProviderHelper();
    private LocationProvider[] allProviders;

    private LocationProviderHelper() {
        initializeProvidersList();
    }

    //We use a method in cases with a lot of boilerplate
    private void initializeProvidersList() {
        if (isTest) {
            allProviders = new LocationProvider[]{new TestLocationProvider()};
            return;
        }
        allProviders = new LocationProvider[]{new FoursquareLocationsProvider()};
    }

    public static LocationProviderHelper getHelper() {
        return instance;
    }

    public static void setTestMode(boolean testMode) {
        if (isTest == testMode) return;
        isTest = testMode;
        instance = new LocationProviderHelper();
        TestLocationProvider.setTestLocations(null);
    }

    public List<DestinationLocation> getLocations(LocationPoint center, Distance range, LocationType type) {

        return Arrays.stream(allProviders)
                .filter(attemptProvider -> attemptProvider.isValidType(type))
                .filter(LocationProvider::isUsable)
                .map(attemptProvider -> attemptProvider.queryLocations(center, range, type))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findFirst()
                .orElse(null);
    }

    public void closeAllProviders() {
        for (LocationProvider prov : allProviders) {
            prov.close();
        }
    }
}
