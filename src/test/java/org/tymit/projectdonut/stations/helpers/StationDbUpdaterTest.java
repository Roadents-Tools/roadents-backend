package org.tymit.projectdonut.stations.helpers;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by ilan on 7/25/16.
 */
public class StationDbUpdaterTest {

    private static final int TOTAL_TEST_CHAINS = 5 * 10;

    @Before
    public void setTest() {
        StationRetriever.setTestMode(true);
        LocationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testDbUpdate() throws Exception {
        //Make sure we have no stations to begin with
        List<TransStation> stations = StationRetriever.getStations(null, 0, null, null);
        Assert.assertEquals(0, stations.size());

        Assert.assertTrue(StationDbUpdater.getUpdater().updateStationsSync());

        stations = StationRetriever.getStations(null, 0, null, null);

        Assert.assertEquals(TOTAL_TEST_CHAINS, stations.size());
    }

    @Test
    public void testDbUpdateAsync() throws Exception {
        //Make sure we have no stations to begin with
        List<TransStation> stations = StationRetriever.getStations(null, 0, null, null);
        Assert.assertEquals(0, stations.size());

        CompletableFuture<Boolean> asyncResult = StationDbUpdater.getUpdater().updateStationsAsync();
        while (!asyncResult.isDone()) {

        }

        Assert.assertTrue(asyncResult.get());

        stations = StationRetriever.getStations(null, 0, null, null);

        Assert.assertEquals(TOTAL_TEST_CHAINS, stations.size());
    }

    @Test
    public void testDbUpdateBackground() throws Exception {
        //Make sure we have no stations to begin with
        List<TransStation> stations = StationRetriever.getStations(null, 0, null, null);
        Assert.assertEquals(0, stations.size());

        StationDbUpdater.getUpdater().setBackgroundInterval(10);
        Thread.sleep(15); //Gurantee we fire at least once
        while (StationDbUpdater.getUpdater().getLastUpdated() < 0) {
            //Gurantee we finish
        }

        stations = StationRetriever.getStations(null, 0, null, null);

        Assert.assertTrue(stations.size() > 0);
    }

    @After
    public void undoTest() {
        StationRetriever.setTestMode(false);
        LocationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}