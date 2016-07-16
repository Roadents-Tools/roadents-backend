package org.tymit.projectdonut.locations.providers;

import org.junit.Assert;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ilan on 7/14/16.
 */
public class TestLocationProvider implements LocationProvider {
    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public boolean isValidType(LocationType type) {
        return true;
    }

    @Override
    public List<DestinationLocation> queryLocations(double[] center, double range, LocationType type) {
        final double[][] mulipliers = new double[][]{
                new double[]{.01, 0},
                new double[]{-.01, 0},
                new double[]{0, .01},
                new double[]{0, -.01}
        };
        List<DestinationLocation> rval = new ArrayList<>(4);
        for (double[] muliplier : mulipliers) {
            double newLat = center[0] + range * muliplier[0];
            double newLong = center[1] + range * muliplier[1];
            double[] newCenter = new double[]{newLat, newLong};
            Assert.assertTrue(LocationUtils.distanceBetween(newCenter, center, true) <= range);

            String name = String.format("Test Dest: QueryCenter = (%f,%f), Type = %s, Additive = (%f,%f)",
                    center[0], center[1],
                    type.getEncodedname(),
                    range * muliplier[0], range * muliplier[1]
            );

            rval.add(new DestinationLocation(name, type, newCenter));
        }
        return rval;
    }
}
