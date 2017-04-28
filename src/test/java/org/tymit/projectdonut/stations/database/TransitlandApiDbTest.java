package org.tymit.projectdonut.stations.database;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.List;

/**
 * Created by ilan on 2/4/17.
 */
public class TransitlandApiDbTest {

    private static final double[] TEST_COORDS = new double[] { 32.8801, -117.2340 };
    private static final double TEST_RANGE = 1;
    private static final TransChain TEST_CHAIN = new TransChain("r-9mue-101 TripID: 12050379");
    private TransitlandApiDb instance;

    @Before
    public void setup() {
        instance = new TransitlandApiDb();
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testRegion() {
        List<TransStation> stations = instance.queryStations(TEST_COORDS, TEST_RANGE, null);
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
        List<TransStation> stations = instance.queryStations(null, 0, TEST_CHAIN);
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
