package org.tymit.projectdonut.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ilan on 7/14/16.
 */
public class LocationUtilsTest {

    @Test
    public void walkTimeDistanceConversions() throws Exception {
        long testTime = 1000L * 60 * 60; //1 hour

        double calcedDistanceMiles = LocationUtils.timeToWalkDistance(testTime, true);
        double calcedDistanceKm = LocationUtils.timeToWalkDistance(testTime, false);
        Assert.assertEquals(1.609, calcedDistanceKm / calcedDistanceMiles, 0.0001);

        long convertedBackMiles = LocationUtils.distanceToWalkTime(calcedDistanceMiles, true);
        long convertedBackKm = LocationUtils.distanceToWalkTime(calcedDistanceKm, false);
        Assert.assertEquals(testTime, convertedBackKm);
        Assert.assertEquals(testTime, convertedBackMiles);
    }

}