package org.tymit.projectdonut.utils;

import org.tymit.projectdonut.model.database.DatabaseID;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimePoint;

public class TimeUtils {

    public static int packTimePoint(TimePoint p) {
        return packTime(p.getHour(), p.getMinute(), p.getSecond());
    }

    public static int packTime(int hour, int minute, int second) {
        return (hour % 24) * 3600 + minute * 60 + second;
    }

    public static int packSchedulePoint(SchedulePoint p) {
        return packTime(p.getHour(), p.getMinute(), p.getSecond());
    }

    public static SchedulePoint unpackPoint(int packed, boolean[] valid, long fuzz, DatabaseID id) {
        int[] unpacked = unpackTime(packed);
        return new SchedulePoint(unpacked[0], unpacked[1], unpacked[2], valid, fuzz, id);
    }

    public static int[] unpackTime(int packed) {
        return new int[] {
                (packed / 3600) % 24,
                (packed / 60) % 60,
                packed % 60
        };
    }
}
