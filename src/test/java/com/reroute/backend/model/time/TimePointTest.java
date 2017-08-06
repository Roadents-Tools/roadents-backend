package com.reroute.backend.model.time;

import org.junit.Assert;
import org.junit.Test;

public class TimePointTest {

    private static final long testMillis = 1000000000000000L;

    @Test
    public void testTimeUntil() throws Exception {
        TimePoint t1 = TimePoint.NULL;
        TimePoint t2 = new TimePoint(testMillis, "GMT");
        Assert.assertEquals(testMillis, t1.timeUntil(t2).getDeltaLong());
        Assert.assertEquals(testMillis * -1, t2.timeUntil(t1).getDeltaLong());
    }
}