package org.tymit.projectdonut.model;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ilan on 7/9/16.
 */
public class TimeModelTest {

    private static final long TEST_TIME = 1469678207308l;

    @Test
    public void testUnixConversion() {
        TimeModel start = TimeModel.fromUnixTime(TEST_TIME);
        long fromTimeModel = start.getUnixTime();
        Assert.assertEquals(TEST_TIME, fromTimeModel);
        TimeModel fromNewUnix = TimeModel.fromUnixTime(fromTimeModel);
        Assert.assertEquals(start, fromNewUnix);
    }

    @Test
    public void testTimeComparison() throws Exception {
        TimeModel a = TimeModel.fromUnixTime(TEST_TIME).set(TimeModel.DAY_OF_MONTH, 1);
        TimeModel b = TimeModel.fromUnixTime(TEST_TIME).set(TimeModel.DAY_OF_MONTH, 2);
        long bCa = b.compareTo(a);
        Assert.assertTrue(bCa > 0);
        long aCb = a.compareTo(b);
        Assert.assertTrue(aCb < 0);

        a = TimeModel.fromUnixTime(915192000l * 1000l); //Friday, January 1st, 1999, at exactly noon
        Assert.assertEquals(6, a.get(TimeModel.DAY_OF_WEEK));

        b = b.set(TimeModel.DAY_OF_WEEK, 7);
        Assert.assertTrue(b.compareTo(a) > 0);
        Assert.assertTrue(a.compareTo(b) < 0);
    }

    @Test
    public void testTimeMath() {
        TimeModel a = TimeModel.empty().set(TimeModel.MINUTE, 5);
        TimeModel b = TimeModel.empty().set(TimeModel.MINUTE, 10);
        long compVal = a.compareTo(b);
        boolean compare = compVal < 5 * 60 * 1000;
        Assert.assertTrue(compare);
    }

}