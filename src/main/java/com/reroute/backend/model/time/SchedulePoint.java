package com.reroute.backend.model.time;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.database.DatabaseObject;

import java.util.Arrays;

/**
 * A single repeating instance when a TransChain arrives at a TransStation.
 * Created by ilan on 2/6/17.
 */
public class SchedulePoint implements DatabaseObject {

    private final boolean[] validDays; //len 7 array, sun-sat, true on valid days
    private final int time; //0 to 86400
    private final long fuzz;
    private final DatabaseID id;

    /**
     * Constructs a new, non-database-connected schedule point.
     *
     * @param hour      the hour the chain arrives at the station, 0-23
     * @param minute    the minute the chain arrives at the station, 0-59
     * @param second    the second the chain arrives at the station, 0-59
     * @param validDays an array of booleans, length 7, with each item representing whether this schedule is valid on
     *                  that day. For example, if validDays[2] = true, this schedule is valid on Tuesdays.
     * @param fuzz      how far after the passed time that the chain will wait, in milliseconds
     */
    public SchedulePoint(int hour, int minute, int second, boolean[] validDays, long fuzz) {
        this(hour * 3600 + minute * 60 + second, validDays, fuzz, null);
    }

    /**
     * Constructs a new, database-connected schedule point.
     *
     * @param time      the time the chain arrives at the stations, in seconds since midnight.
     * @param validDays an array of booleans, length 7, with each item representing whether this schedule is valid on
     *                  that day. For example, if validDays[2] = true, this schedule is valid on Tuesdays.
     * @param fuzz      how far after the passed time that the chain will wait, in milliseconds
     * @param id        the database ID of this schedule point
     */
    public SchedulePoint(int time, boolean[] validDays, long fuzz, DatabaseID id) {
        if (validDays == null) {
            validDays = new boolean[7];
            Arrays.fill(validDays, true);
        }
        if (validDays.length != 7)
            throw new IllegalArgumentException("There are 7 days in the week. You passed: " + validDays.length);
        if (time < 0 || time >= 86400)
            throw new IllegalArgumentException("There are 86400 seconds in the day. You passed " + time);
        if (fuzz < 0) throw new IllegalArgumentException("Cannot time travel.");
        this.validDays = validDays;
        this.time = time;
        this.fuzz = fuzz;
        this.id = id;
    }

    /**
     * Constructs a new, database-connected schedule point.
     * @param hour the hour the chain arrives at the station, 0-23
     * @param minute the minute the chain arrives at the station, 0-59
     * @param second the second the chain arrives at the station, 0-59
     * @param validDays an array of booleans, length 7, with each item representing whether this schedule is valid on
     *                  that day. For example, if validDays[2] = true, this schedule is valid on Tuesdays.
     * @param fuzz how far after the passed time that the chain will wait, in milliseconds
     * @param id the database ID of this schedule point
     */
    public SchedulePoint(int hour, int minute, int second, boolean[] validDays, long fuzz, DatabaseID id) {
        this(hour * 3600 + minute * 60 + second, validDays, fuzz, id);
    }

    /**
     * Calculates the next point in time that this schedule point is valid.
     * @param base the start time
     * @return the next time after base that this schedule point is valid
     */
    public TimePoint nextValidTime(TimePoint base) {
        if (isValidTime(base)) return base;
        TimePoint rval = base.withTime(getPackedTime());
        if (rval.isBefore(base)) rval = rval.addDay();
        if (!validDays[rval.getDayOfWeek()]) {
            for (int i = rval.getDayOfWeek(); i < rval.getDayOfWeek() + 7; i++) {
                int day = (rval.getDayOfWeek() + i) % 7;
                if (validDays[day]) {
                    rval = rval.withDayOfWeek(day);
                    break;
                }
            }
        }
        if (rval.isBefore(base)) rval = rval.addWeek();
        return rval;
    }

    /**
     * Checks whether a point in time is valid for this schedule point.
     * @param base the time to check
     * @return whether the chain arrives at the station at that time
     */
    public boolean isValidTime(TimePoint base) {
        return validDays[base.getDayOfWeek()] && Math.abs(base.getPackedTime() - time) <= fuzz;
    }

    /**
     * Gets the valid days of this schedule point.
     * @return the valid days of this schedule point
     */
    public boolean[] getValidDays() {
        return validDays;
    }

    /**
     * Gets the packed representation of this time, in seconds-since-midnight format.
     *
     * @return the time of arrival, between 0 and 86400
     */
    public int getPackedTime() {
        return time;
    }

    /**
     * Gets the hour of this schedule point.
     * @return the hour of this schedule point
     */
    public int getHour() {
        return time / 3600;
    }

    /**
     * Gets the minute of this schedule point.
     * @return the minute of this schedule point
     */
    public int getMinute() {
        return (time / 60) % 60;
    }

    /**
     * Gets the second of this schedule point.
     * @return the second of this schedule point
     */
    public int getSecond() {
        return time % 60;
    }

    /**
     * Gets the fuzz of this schedule point.
     * @return the fuzz of this schedule point
     */
    public long getFuzz() {
        return fuzz;
    }

    @Override
    public DatabaseID getID() {
        return id;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(validDays);
        result = 31 * result + time;
        result = 31 * result + (int) (fuzz ^ (fuzz >>> 32));
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchedulePoint that = (SchedulePoint) o;

        if (time != that.time) return false;
        if (fuzz != that.fuzz) return false;
        if (!Arrays.equals(validDays, that.validDays)) return false;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public String toString() {
        return "SchedulePoint{" +
                "validDays=" + Arrays.toString(validDays) +
                ", time=" + time +
                ", fuzz=" + fuzz +
                ", id=" + id +
                '}';
    }
}
