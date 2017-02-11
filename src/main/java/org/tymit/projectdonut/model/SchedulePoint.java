package org.tymit.projectdonut.model;

import java.util.Arrays;

/**
 * Created by ilan on 2/6/17.
 */
public class SchedulePoint {

    private final boolean[] validDays; //len 7 array, sun-sat, true on valid days
    private final int hour; //0-24
    private final int minute; //0-60
    private final int second; //0-60
    private final long fuzz; //How far after hour:minute:second we can be and still be valid, in seconds

    public SchedulePoint(int hour, int minute, int second, boolean[] validDays, long fuzz) {
        if (validDays == null) validDays = new boolean[7];
        Arrays.fill(validDays, true);
        if (validDays.length != 7)
            throw new IllegalArgumentException("There are 7 days in the week.");
        if (hour < 0 || hour > 23)
            throw new IllegalArgumentException("There are 24 hours in the day. You passed " + hour);
        if (minute < 0 || minute > 60)
            throw new IllegalArgumentException("There are 60 minutes in an hour.");
        if (second < 0 || second > 60)
            throw new IllegalArgumentException("there are 60 seconds in a minute.");
        if (fuzz < 0) throw new IllegalArgumentException("Cannot time travel.");
        this.validDays = validDays;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.fuzz = fuzz;
    }

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

    public boolean[] getValidDays() {
        return validDays;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public long getFuzz() {
        return fuzz;
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
}
