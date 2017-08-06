package com.reroute.backend.stations.transitland;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.utils.LoggingUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Objects;

/**
 * Created by ilan on 2/4/17.
 */
public class TransitlandApiDbTest {

    private static final double[] TEST_COORDS = new double[] { 32.8801, -117.2340 };
    private static final double TEST_RANGE = 1;
    private TransitlandApiDb instance;

    private static TransChain TEST_CHAIN = null;

    @Before
    public void setup() {
        instance = new TransitlandApiDb();
        LoggingUtils.setPrintImmediate(true);
        TEST_CHAIN = instance.queryStations(new StartPoint(TEST_COORDS), new Distance(1, DistanceUnits.MILES), null, null, null)
                .stream()
                .map(TransStation::getChain)
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);

    }

    @Test
    public void testRegion() {
        List<TransStation> stations = instance.queryStations(new StartPoint(TEST_COORDS), new Distance(TEST_RANGE, DistanceUnits.MILES), null, null, null);
        Assert.assertNotEquals(0, stations.size());
        stations.forEach(station -> {
            Assert.assertNotNull(station.getName());
            Assert.assertTrue(station.getCoordinates()[0] < 40);
            Assert.assertTrue(station.getCoordinates()[0] > 20);
            Assert.assertTrue(station.getCoordinates()[1] < -110);
            Assert.assertTrue(station.getCoordinates()[1] > -125);
            Assert.assertNotEquals(0, station.getChain().getStations().size());
            Assert.assertNotEquals(0, station.getSchedule().size());
        });
    }

    @Test
    public void testChain() {
        List<TransStation> stations = instance.queryStations(null, new Distance(0, DistanceUnits.METERS), null, null, TEST_CHAIN);
        Assert.assertNotEquals(0, stations.size());
        stations.forEach(station -> {
            Assert.assertEquals(TEST_CHAIN, station.getChain());
            Assert.assertTrue(TEST_CHAIN.getStations().contains(station));
            Assert.assertNotNull(station.getName());
            Assert.assertNotEquals(0, station.getSchedule().size());
        });
    }

    @After
    public void teardown() {
        instance.close();
        LoggingUtils.setPrintImmediate(false);
    }
}
