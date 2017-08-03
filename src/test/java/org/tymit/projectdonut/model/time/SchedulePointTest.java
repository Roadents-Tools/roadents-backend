package org.tymit.projectdonut.model.time;

import org.junit.Assert;
import org.junit.Test;

public class SchedulePointTest {


    @Test
    public void nextValidTime() throws Exception {
        TimePoint testTime = TimePoint.now();
        SchedulePoint testPoint = new SchedulePoint(
                (testTime.getHour() + 1) % 24, testTime.getMinute(), testTime.getSecond(),
                null, 60
        );

        TimePoint nextTime = testPoint.nextValidTime(testTime);
        Assert.assertEquals(nextTime, testPoint.nextValidTime(testTime));
        Assert.assertEquals(60 * 60 * 1000L, testTime.timeUntil(testPoint.nextValidTime(testTime)).getDeltaLong());
    }

}