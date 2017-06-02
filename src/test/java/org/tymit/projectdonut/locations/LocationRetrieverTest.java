package org.tymit.projectdonut.locations;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class LocationRetrieverTest {

    @Test
    public void getLocations() throws Exception {


        double[] testPt = new double[]{37.3532801, -122.0052875};
        double testRange = 1;
        LocationType testType = new LocationType("Food", "food");


        long startTime = System.currentTimeMillis();
        List<DestinationLocation> locations = LocationRetriever.getLocations(testPt, testRange, testType, null);
        long midTime = System.currentTimeMillis();
        List<DestinationLocation> l2 = LocationRetriever.getLocations(testPt, testRange, testType, null);
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(locations.size(), l2.size());
        for (DestinationLocation pt : locations) Assert.assertTrue(l2.contains(pt));
    }

}