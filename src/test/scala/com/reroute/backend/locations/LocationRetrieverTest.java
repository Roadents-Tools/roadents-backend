package com.reroute.backend.locations;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class LocationRetrieverTest {

    @Test
    public void getLocations() {


        LocationPoint testPt = new StartPoint(new double[] { 37.3532801, -122.0052875 });
        Distance testRange = new Distance(1, DistanceUnits.MILES);
        LocationType testType = new LocationType("Food", "food");


        long startTime = System.currentTimeMillis();
        List<DestinationLocation> locations = LocationRetriever.getLocations(testPt, testRange, testType);
        long midTime = System.currentTimeMillis();
        List<DestinationLocation> l2 = LocationRetriever.getLocations(testPt, testRange, testType);
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(locations.size(), l2.size());
        for (DestinationLocation pt : locations) Assert.assertTrue(l2.contains(pt));
    }

}