package com.reroute.backend.model.time;

import com.reroute.backend.model.database.DatabaseID;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SchedulePointTest {


    @Test
    public void testGetSet() {
        TimePoint testTime = TimePoint.now();
        int hour = testTime.getHour();
        int minute = testTime.getMinute();
        int second = testTime.getSecond();
        boolean[] validDays = new boolean[7];
        Arrays.fill(validDays, false);
        validDays[testTime.getDayOfWeek()] = true;
        long fuzz = testTime.getMilliseconds() % 1000;
        DatabaseID id = new DatabaseID("SpoonDB", "1");

        SchedulePoint testPoint = new SchedulePoint(hour, minute, second, validDays, fuzz, id);
        Assert.assertEquals(hour, testPoint.getHour());
        Assert.assertEquals(minute, testPoint.getMinute());
        Assert.assertEquals(second, testPoint.getSecond());
        Assert.assertArrayEquals(validDays, testPoint.getValidDays());
        Assert.assertEquals(fuzz, testPoint.getFuzz());
        Assert.assertEquals(id, testPoint.getID());
    }

    @Test
    public void nextValidTime() {
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