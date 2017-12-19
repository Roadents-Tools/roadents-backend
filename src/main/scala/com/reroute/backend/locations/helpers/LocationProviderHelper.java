package com.reroute.backend.locations.helpers;

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
 * The delegater and aggregator of all LocationProviders.
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
        allProviders = new LocationProvider[] { new TestLocationProvider() };
    }

    /**
     * Gets the instance of the delegater.
     *
     * @return the current helper instance
     */
    public static LocationProviderHelper getHelper() {
        return instance;
    }

    /**
     * Sets whether or not the Location Providers hould act in a test context or not.
     * @param testMode whether or not the helper is in a test
     */
    public static void setTestMode(boolean testMode) {
        if (isTest == testMode) return;
        isTest = testMode;
        instance = new LocationProviderHelper();
        TestLocationProvider.setTestLocations(null);
    }

    /**
     * Runs a query against the current LocationProviders.
     * @param center the center of the query
     * @param range the distance around the center to query
     * @param type the type of location to query
     * @return the locations meeting the query
     */
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

    /**
     * Closes all providers currently opened.
     */
    public void closeAllProviders() {
        for (LocationProvider prov : allProviders) {
            prov.close();
        }
    }
}
