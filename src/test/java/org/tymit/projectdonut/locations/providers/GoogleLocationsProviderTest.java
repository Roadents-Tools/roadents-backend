package org.tymit.projectdonut.locations.providers;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class GoogleLocationsProviderTest {


    @Test
    public void queryLocations() throws Exception {

        double[] testPt = new double[]{37.3532801, -122.0052875};
        double testRange = 1;
        LocationType testType = new LocationType("Food", "food");

        GoogleLocationsProvider provider = new GoogleLocationsProvider();
        List<DestinationLocation> testDests = provider.queryLocations(testPt, testRange, testType);
        Assert.assertTrue(testDests.size() > 0);
        testDests.forEach(dest -> {
                    double dist = LocationUtils.distanceBetween(dest.getCoordinates(), testPt, true);
                    System.out.printf("%s @ (%f, %f), %f miles from center.\n",
                            dest.getName(),
                            dest.getCoordinates()[0], dest.getCoordinates()[1],
                            dist);
                    Assert.assertTrue(dist <= testRange);
                }
        );



    }

}