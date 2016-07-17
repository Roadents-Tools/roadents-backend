package org.tymit.restcontroller.jsonconvertion;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.StartPoint;

/**
 * Created by ilan on 7/17/16.
 */
public class StartPointJsonConverterTest {

    @Test
    public void testBackAndForth() {
        StartPoint pt = new StartPoint(new double[]{3234.993499499, 234.5});
        String json = new StartPointJsonConverter().toJson(pt);
        StartPoint ptTest = new StartPointJsonConverter().fromJson(json);
        Assert.assertEquals(pt, ptTest);
    }

}