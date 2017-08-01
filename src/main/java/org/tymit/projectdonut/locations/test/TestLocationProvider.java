package org.tymit.projectdonut.locations.test;

import org.junit.Assert;
import org.tymit.projectdonut.locations.interfaces.LocationProvider;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/14/16.
 */
public class TestLocationProvider implements LocationProvider {

    private static final double[][] MULTIPLIERS = new double[][]{
            new double[]{.01, 0},
            new double[]{-.01, 0},
            new double[]{0, .01},
            new double[]{0, -.01}
    };
    public static final int DEFAULT_POINTS_PER_QUERY = MULTIPLIERS.length;
    private static Collection<DestinationLocation> testLocations = null;

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
                        && LocationUtils.distanceBetween(center, location).inMiles() < range.inMiles() + 0.001)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {

    }

    private static List<DestinationLocation> buildNullLocations(LocationPoint center, Distance range, LocationType type) {

        List<DestinationLocation> rval = new ArrayList<>(DEFAULT_POINTS_PER_QUERY);
        for (double[] muliplier : MULTIPLIERS) {
            double newLat = center.getCoordinates()[0] + range.inMiles() * muliplier[0];
            double newLong = center.getCoordinates()[1] + range.inMiles() * muliplier[1];
            double[] newCenter = new double[]{newLat, newLong};
            Assert.assertTrue(LocationUtils.distanceBetween(new StartPoint(newCenter), center)
                    .inMeters() <= range.inMeters());

            String name = String.format("Test Dest: QueryCenter = (%f,%f), Type = %s, Additive = (%f,%f)",
                    center.getCoordinates()[0], center.getCoordinates()[1],
                    type.getEncodedname(),
                    range.inMiles() * muliplier[0], range.inMiles() * muliplier[1]
            );

            rval.add(new DestinationLocation(name, type, newCenter));
        }
        return rval;
    }
}
