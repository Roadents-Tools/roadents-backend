package org.tymit.projectdonut.stations.gtfs;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/22/16.
 */
public class GtfsProviderTest {

    private static final String TEST_ZIP = GtfsProvider.GTFS_ZIPS[0];

    @Before
    public void setUp() {
        LoggingUtils.setPrintImmediate(true);

    }

    @Test
    public void testGtfsProvider() throws Exception {

        GtfsProvider provider = new GtfsProvider(TEST_ZIP);
        Map<TransChain, List<TransStation>> output = provider.getUpdatedStations();
        Assert.assertTrue(output.size() > 0);
        for (TransChain chain : output.keySet()) {
            int outputSize = output.get(chain).size();
            int chainSize = chain.getStations().size();
            Assert.assertEquals(outputSize, chainSize);
            for (TransStation station : chain.getStations()) {
                Assert.assertTrue(output.get(chain).contains(station));
            }
        }

        TransChain testChain = output.keySet().toArray(new TransChain[1])[0];
        System.out.printf("Chain:%s. %d stations.\n\n", testChain.getName(), testChain.getStations().size());
        for (TransStation station : testChain.getStations()) {
            System.out.println(station.toString());
            for (SchedulePoint tm : station.getSchedule()) {
                System.out.println(tm);
            }
        }
    }

    @After
    public void undoSetup() {
        LoggingUtils.setPrintImmediate(false);
    }


}