package org.tymit.restcontroller.jsonconvertion;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;

/**
 * Created by ilan on 7/17/16.
 */
public class DestinationJsonConverterTest {

    @Test
    public void testBackAndForth() {
        DestinationLocation pt = new DestinationLocation("Testdest", new LocationType("test", "test"), new double[]{3234.993499499, 234.5});
        String json = new DestinationJsonConverter().toJson(pt);
        DestinationLocation ptTest = new DestinationJsonConverter().fromJson(json);
        Assert.assertEquals(pt, ptTest);
    }

}