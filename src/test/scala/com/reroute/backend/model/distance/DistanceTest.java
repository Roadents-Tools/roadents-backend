package com.reroute.backend.model.distance;

import org.junit.Assert;
import org.junit.Test;

public class DistanceTest {

    @Test
    public void math() {
        Distance a = new Distance(1, DistanceUnits.METERS);
        Distance b = new Distance(2, DistanceUnits.METERS);
        Assert.assertEquals(new Distance(3, DistanceUnits.METERS), a.plus(b));
        Assert.assertEquals(new Distance(1, DistanceUnits.METERS), b.minus(a));
        Assert.assertEquals(b, a.mul(2));
        Assert.assertEquals(a, b.div(2));
    }

    @Test
    public void comparisons() {
        Distance a = new Distance(1, DistanceUnits.METERS);
        Distance b = new Distance(1.01, DistanceUnits.METERS);
        Assert.assertTrue(a.deltaEqual(b, new Distance(.1, DistanceUnits.METERS)));
        Assert.assertFalse(a.deltaEqual(b, new Distance(.001, DistanceUnits.METERS)));

        Distance zed = new Distance(.000000000000000001, DistanceUnits.METERS);
        Assert.assertTrue(zed.isZero());

        Distance a2 = new Distance(1, DistanceUnits.METERS);
        Assert.assertEquals(a, a2);
        Assert.assertEquals(a.hashCode(), a2.hashCode());
        Assert.assertEquals(a.toString(), a2.toString());
        Assert.assertNotEquals(a.toString(), b.toString());
        Assert.assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void conversions() {
        Distance ft = new Distance(1, DistanceUnits.FEET);
        Assert.assertEquals(1, ft.inFeet(), Distance.ERROR_MARGIN.inMeters());
        Assert.assertEquals(DistanceUnits.FEET.toMeters, ft.inMeters(), Distance.ERROR_MARGIN.inMeters());

        Distance mi = new Distance(1, DistanceUnits.MILES);
        Assert.assertEquals(1, mi.inMiles(), Distance.ERROR_MARGIN.inMeters());
        Assert.assertEquals(DistanceUnits.MILES.toMeters, mi.inMeters(), Distance.ERROR_MARGIN.inMeters());

        Distance km = new Distance(1, DistanceUnits.KILOMETERS);
        Assert.assertEquals(1, km.inKilometers(), Distance.ERROR_MARGIN.inMeters());
        Assert.assertEquals(DistanceUnits.KILOMETERS.toMeters, km.inMeters(), Distance.ERROR_MARGIN.inMeters());

        Distance m = new Distance(1, DistanceUnits.METERS);
        Assert.assertEquals(1, m.inMeters(), Distance.ERROR_MARGIN.inMeters());
        Assert.assertEquals(DistanceUnits.METERS.toMeters, m.inMeters(), Distance.ERROR_MARGIN.inMeters());
    }
}