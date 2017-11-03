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
    private final int hour; //0-24
    private final int minute; //0-60
    private final int second; //0-60
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
        if (validDays == null) {
            validDays = new boolean[7];
            Arrays.fill(validDays, true);
        }
        if (validDays.length != 7)
            throw new IllegalArgumentException("There are 7 days in the week. You passed: " + validDays.length);
        if (hour < 0 || hour > 23)
            throw new IllegalArgumentException("There are 24 hours in the day. You passed " + hour);
        if (minute < 0 || minute > 60)
            throw new IllegalArgumentException("There are 60 minutes in an hour. You passed " + minute);
        if (second < 0 || second > 60)
            throw new IllegalArgumentException("there are 60 seconds in a minute. You passed " + second);
        if (fuzz < 0) throw new IllegalArgumentException("Cannot time travel.");
        this.validDays = validDays;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.fuzz = fuzz;
        this.id = null;
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
        if (validDays == null) {
            validDays = new boolean[7];
            Arrays.fill(validDays, true);
        }
        if (validDays.length != 7)
            throw new IllegalArgumentException("There are 7 days in the week. You passed: " + validDays.length);
        if (hour < 0 || hour > 23)
            throw new IllegalArgumentException("There are 24 hours in the day. You passed " + hour);
        if (minute < 0 || minute > 60)
            throw new IllegalArgumentException("There are 60 minutes in an hour. You passed " + minute);
        if (second < 0 || second > 60)
            throw new IllegalArgumentException("there are 60 seconds in a minute. You passed " + second);
        if (fuzz < 0) throw new IllegalArgumentException("Cannot time travel.");
        this.validDays = validDays;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.fuzz = fuzz;
        this.id = id;
    }

    /**
     * Calculates how long after this schedule point will another arrival happen.
     * @param other the other schedule point
     * @return the difference in time between an arrival of this point and the arrival of another point
     */
    @Deprecated
    public TimeDelta timeBefore(SchedulePoint other) {
        TimePoint thisBase = nextValidTime(TimePoint.NULL.addDay());
        TimePoint otherBase = other.nextValidTime(thisBase);
        return thisBase.timeUntil(otherBase);
    }

    /**
     * Calculates the next point in time that this schedule point is valid.
     * @param base the start time
     * @return the next time after base that this schedule point is valid
     */
    public TimePoint nextValidTime(TimePoint base) {
        if (isValidTime(base)) return base;
        TimePoint rval = base.withHour(hour)
                .withMinute(minute)
                .withSecond(second);
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
        if (!validDays[base.getDayOfWeek()]) return false;
        TimePoint valid = base.withHour(hour)
                .withMinute(minute)
                .withSecond(second);
        long diff = base.timeUntil(valid).getDeltaLong();
        return diff > 0 && diff < fuzz;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(getValidDays());
        result = 31 * result + getHour();
        result = 31 * result + getMinute();
        result = 31 * result + getSecond();
        result = 31 * result + (int) (getFuzz() ^ (getFuzz() >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchedulePoint that = (SchedulePoint) o;

        if (getHour() != that.getHour()) return false;
        if (getMinute() != that.getMinute()) return false;
        if (getSecond() != that.getSecond()) return false;
        if (getFuzz() != that.getFuzz()) return false;
        return Arrays.equals(getValidDays(), that.getValidDays());
    }

    @Override
    public String toString() {
        return "SchedulePoint{" +
                "validDays=" + Arrays.toString(validDays) +
                ", hour=" + hour +
                ", minute=" + minute +
                ", second=" + second +
                ", fuzz=" + fuzz +
                '}';
    }

    /**
     * Gets the valid days of this schedule point.
     * @return the valid days of this schedule point
     */
    public boolean[] getValidDays() {
        return validDays;
    }

    /**
     * Gets the hour of this schedule point.
     * @return the hour of this schedule point
     */
    public int getHour() {
        return hour;
    }

    /**
     * Gets the minute of this schedule point.
     * @return the minute of this schedule point
     */
    public int getMinute() {
        return minute;
    }

    /**
     * Gets the second of this schedule point.
     * @return the second of this schedule point
     */
    public int getSecond() {
        return second;
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
}
