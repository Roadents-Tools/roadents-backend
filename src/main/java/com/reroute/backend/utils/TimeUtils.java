package com.reroute.backend.utils;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimePoint;

import java.util.stream.IntStream;

/**
 * Static utility class for time-based helper methods.
 */
public class TimeUtils {

    /**
     * Packs the time information about a particular TimePoint into an int.
     *
     * @param p the TimePoint to extract the time from
     * @return an int representing the seconds this time is since midnight
     */
    public static int packTimePoint(TimePoint p) {
        return packTime(p.getHour(), p.getMinute(), p.getSecond());
    }

    /**
     * Packs a time into a single int.
     * @param hour the hour of the time
     * @param minute the minute of the time
     * @param second the second of the time
     * @return the seconds since midnight this time represents
     */
    public static int packTime(int hour, int minute, int second) {
        return (hour % 24) * 3600 + minute * 60 + second;
    }

    /**
     * Packs a SchedulePoint's time component into a single int.
     * @param p the schedule point to extract time information from
     * @return the packed time info
     */
    public static int packSchedulePoint(SchedulePoint p) {
        return packTime(p.getHour(), p.getMinute(), p.getSecond());
    }

    /**
     * Unpacks information into a SchedulePoint instance.
     * @param packed the packed time int, in seconds since midnight
     * @param valid a length 7 array of when this point is valid
     * @param fuzz how long the bus will wait at the station
     * @param id the ID of the unpacked record
     * @return a SchedulePoint containing the unpacked form of the information passed
     */
    public static SchedulePoint unpackPoint(int packed, boolean[] valid, long fuzz, DatabaseID id) {
        int[] unpacked = unpackTime(packed);
        return new SchedulePoint(unpacked[0], unpacked[1], unpacked[2], valid, fuzz, id);
    }

    /**
     * Unpacks a packed time int into its hour, minute, and second components.
     * @param packed the packed time, in seconds since midnight
     * @return an array containing {hour, minute, second}
     */
    public static int[] unpackTime(int packed) {
        return new int[] {
                (packed / 3600) % 24,
                (packed / 60) % 60,
                packed % 60
        };
    }

    /**
     * Converts a bit string to a boolean array.
     * Each character in the stream maps to 1 element of the array; '0' maps to false, and anything else maps to true.
     *
     * @param str the bit string to use
     * @return the boolean array representing the string
     */
    public static boolean[] bitStrToBools(String str) {
        boolean[] rval = new boolean[str.length()];

        IntStream.range(0, str.length())
                .boxed()
                .parallel()
                .forEach(i -> rval[i] = str.charAt(i) != '0');

        return rval;
    }

    /**
     * Converts a boolean array to a string.
     * Each element is mapped to a '1' if true or a '0' if false.
     *
     * @param bools the boolean array to convert
     * @return the string containing 1's and 0's that maps to bools
     */
    public static String boolsToBitStr(boolean[] bools) {
        return StreamUtils.streamBoolArray(bools)
                .map(bol -> bol ? '1' : '0')
                .collect(StreamUtils.joinToString(""));
    }
}
