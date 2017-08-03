package org.tymit.projectdonut.utils;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.time.TimeDelta;

/**
 * Created by ilan on 7/14/16.
 */
public class LocationUtilsTest {

    @Test
    public void timeBetween() throws Exception {
        double[] p1 = { 43, -71 };
        double[] p2 = { 42, -70 };
        double distance = LocationUtils.distanceBetween(new StartPoint(p1), new StartPoint(p2)).inMeters();
        TimeDelta timeActual = LocationUtils.timeBetween(new StartPoint(p1), new StartPoint(p2));
        assertEqualsDelta(distance, LocationUtils.timeToWalkDistance(timeActual).inMeters(), 1);
    }

    private static void assertEqualsDelta(double value1, double value2, double delta) {
        Assert.assertTrue(Math.abs(value1 - value2) <= delta);
    }
}