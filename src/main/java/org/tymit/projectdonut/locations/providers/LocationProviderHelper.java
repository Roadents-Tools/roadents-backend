package org.tymit.projectdonut.locations.providers;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationProviderHelper {

    private static boolean isTest = false;
    private static LocationProviderHelper instance = new LocationProviderHelper();
    private final ConcurrentMap<LocationType, Set<LocationProvider>> typeToProviders;
    private LocationProvider[] allProviders;

    private LocationProviderHelper() {
        initializeProvidersList();
        typeToProviders = new ConcurrentHashMap<>();
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

    public List<DestinationLocation> getLocations(double[] center, double range, LocationType type) {
        if (typeToProviders.containsKey(type) && typeToProviders.get(type).size() > 0) {
            return typeToProviders.get(type).stream()
                    .filter(LocationProvider::isUsable)
                    .map(provider -> provider.queryLocations(center, range, type))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(Collections.emptyList());
        }
        for (LocationProvider attemptProvider : allProviders) {
            if (!attemptProvider.isValidType(type)) continue;

            typeToProviders.putIfAbsent(type, Collections.synchronizedSet(new HashSet<>()));

            typeToProviders.get(type).add(attemptProvider);
            if (!attemptProvider.isUsable()) continue;
            List<DestinationLocation> allPoints = attemptProvider.queryLocations(center, range, type);
            if (allPoints != null) return allPoints;
        }
        return null;
    }
}
