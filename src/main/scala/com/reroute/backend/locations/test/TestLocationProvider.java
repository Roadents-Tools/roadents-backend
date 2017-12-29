package com.reroute.backend.locations.test;

import com.reroute.backend.locations.interfaces.LocationProvider;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.utils.LocationUtils;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A location provider for use in tests.
 */
public class TestLocationProvider implements LocationProvider {

    private static final double[][] MULTIPLIERS = new double[][]{
            new double[] { .99, 0 },
            new double[] { -.99, 0 },
            new double[] { 0, .99 },
            new double[] { 0, -.99 }
    };
    private static final int DEFAULT_POINTS_PER_QUERY = MULTIPLIERS.length;
    private static Collection<DestinationLocation> testLocations = null;
    private static Random rng = new Random();

    /**
     * Loads set test locations into the provider instead of creating the default semi-random ones.
     *
     * @param testLocations the locations to load
     */
    public static void setTestLocations(Collection<DestinationLocation> testLocations) {
        TestLocationProvider.testLocations = testLocations;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public boolean isValidType(LocationType type) {
        return true;
    }

    @Override
    public List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type) {
        if (testLocations == null) return buildNullLocations(center, range, type);
        return testLocations.stream()
                .filter(location -> location.getType().equals(type)
                        && LocationUtils.distanceBetween(center, location).inMeters() < range.inMeters() + 0.1)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {

    }

    private static List<DestinationLocation> buildNullLocations(LocationPoint center, Distance range, LocationType type) {

        List<DestinationLocation> rval = new ArrayList<>(DEFAULT_POINTS_PER_QUERY);
        for (double[] muliplier : MULTIPLIERS) {
            double newLat = center.getCoordinates()[0] + LocationUtils.latitudeRange(center, range) * muliplier[0];
            double newLong = center.getCoordinates()[1] + LocationUtils.longitudeRange(center, range) * muliplier[1];
            double[] newCenter = new double[]{newLat, newLong};
            Assert.assertTrue(
                    "Failed in building test destinations.",
                    LocationUtils.distanceBetween(new StartPoint(newCenter), center).inMeters() <= range.inMeters()
            );

            String name = String.format("%s Place %d", type.getVisibleName(), rng.nextInt(100));

            rval.add(new DestinationLocation(name, type, newCenter));
        }
        return rval;
    }
}
