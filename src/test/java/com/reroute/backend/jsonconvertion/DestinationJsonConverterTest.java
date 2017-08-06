package com.reroute.backend.jsonconvertion;

import com.reroute.backend.jsonconvertion.location.DestinationJsonConverter;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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