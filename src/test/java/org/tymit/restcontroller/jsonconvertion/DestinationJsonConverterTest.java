package org.tymit.restcontroller.jsonconvertion;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LoggingUtils;

/**
 * Created by ilan on 7/17/16.
 */
public class DestinationJsonConverterTest {

    @Before
    public void setUpTest() {
        LocationRetriever.setTestMode(true);
        StationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testBackAndForth() {
        DestinationLocation pt = new DestinationLocation("Testdest", new LocationType("test", "test"), new double[]{3234.993499499, 234.5});
        String json = new DestinationJsonConverter().toJson(pt);
        DestinationLocation ptTest = new DestinationJsonConverter().fromJson(json);
        Assert.assertEquals(pt, ptTest);
    }

    @After
    public void undoSetup() {
        LocationRetriever.setTestMode(false);
        StationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}