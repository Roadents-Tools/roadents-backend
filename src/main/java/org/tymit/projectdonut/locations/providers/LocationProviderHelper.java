package org.tymit.projectdonut.locations.providers;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationProviderHelper {

    private static final LocationProvider[] allProviders = initializeProvidersList();

    private static LocationProviderHelper instance = new LocationProviderHelper();

    private ConcurrentMap<LocationType, Set<LocationProvider>> typeToProviders;

    private LocationProviderHelper() {
        typeToProviders = new ConcurrentHashMap<>();
    }

    public static LocationProviderHelper getHelper() {
        return instance;
    }

    //We use a method in cases with a lot of boilerplate
    private static LocationProvider[] initializeProvidersList() {
        return new LocationProvider[]{new GoogleLocationsProvider()};
    }

    public List<DestinationLocation> getLocations(double[] center, double range, LocationType type) {
        if (typeToProviders.getOrDefault(type, null) != null && typeToProviders.get(type).size() > 0) {
            for (LocationProvider provider : typeToProviders.get(type)) {
                if (!provider.isUsable()) continue;
                List<DestinationLocation> allPoints = provider.queryLocations(center, range, type);
                if (allPoints != null) return allPoints;
            }
        }
        for (LocationProvider attemptProvider : allProviders) {
            if (!attemptProvider.isValidType(type)) continue;

            if (typeToProviders.getOrDefault(type, null) == null) {
                typeToProviders.putIfAbsent(type, Collections.synchronizedSet(new HashSet<>()));
            }

            typeToProviders.get(type).add(attemptProvider);
            if (!attemptProvider.isUsable()) continue;
            List<DestinationLocation> allPoints = attemptProvider.queryLocations(center, range, type);
            if (allPoints != null) return allPoints;
        }
        return null;
    }
}
