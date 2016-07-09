package org.tymit.projectdonut.model;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ilan on 7/9/16.
 */
public class TimeModelTest {

    @Test
    public void testTimeModel() throws Exception {
        TimeModel a = TimeModel.now();
        a.set(TimeModel.DAY_OF_MONTH, 1);

        TimeModel b = new TimeModel();
        b.set(TimeModel.DAY_OF_MONTH, 2);
        int bCa = b.compareTo(a);
        Assert.assertTrue(bCa > 0);
        int aCb = a.compareTo(b);
        Assert.assertTrue(aCb < 0);

        a.setUnixTime(915192000l * 1000l); //Friday, January 1st, 1999, at exactly noon
        Assert.assertEquals(6, a.get(TimeModel.DAY_OF_WEEK));

        b.set(TimeModel.DAY_OF_WEEK, 7);
        Assert.assertTrue(b.compareTo(a) > 0);
        Assert.assertTrue(a.compareTo(b) < 0);
    }

}