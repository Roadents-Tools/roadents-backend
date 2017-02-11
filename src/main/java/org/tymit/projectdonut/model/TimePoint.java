package org.tymit.projectdonut.model;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Created by ilan on 2/6/17.
 */
public class TimePoint implements Comparable<TimePoint> {

    private static final long SECONDS_TO_MILLIS = 1000;
    private static final long MINUTES_TO_MILLIS = 60 * SECONDS_TO_MILLIS;
    private static final long HOURS_TO_MILLIS = 60 * MINUTES_TO_MILLIS;
    private static final long DAYS_TO_MILLIS = 24 * HOURS_TO_MILLIS;
    private static final long WEEKS_TO_MILLIS = 7 * DAYS_TO_MILLIS;

    private final String timeZone;
    private final long unixTime;

    public TimePoint(long unixTime, String timeZone) {
        this.unixTime = unixTime;
        this.timeZone = timeZone;
    }

    public int getYear() {
        return getCalendar().get(Calendar.YEAR);
    }

    private Calendar getCalendar() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(unixTime);
        return cal;
    }

    public int getMonth() {
        return getCalendar().get(Calendar.MONTH);
    }

    /**
     * NEW TIME CREATORS
     **/
    public TimePoint withDayOfMonth(int dayOfMonth) {
        if (dayOfMonth < 0 || dayOfMonth > 31)
            throw new IllegalArgumentException("Day of month invalid.");
        int dayDiff = dayOfMonth - getDayOfMonth();
        long millidiff = dayDiff * DAYS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone);
    }

    public int getDayOfMonth() {
        return getCalendar().get(Calendar.DAY_OF_MONTH);
    }

    public TimePoint withDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 0 || dayOfWeek > 7)
            throw new IllegalArgumentException("Day of week invalid.");
        int dayDiff = dayOfWeek - getDayOfWeek();
        long millidiff = dayDiff * DAYS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone);
    }

    public int getDayOfWeek() {
        //We go 0-6 instead of Calendar's 1-7
        return getCalendar().get(Calendar.DAY_OF_WEEK) - 1;
    }

    public TimePoint withHour(int hour) {
        if (hour < 0 || hour > 23)
            throw new IllegalArgumentException("Hour invalid.");
        int hourDiff = hour - getHour();
        long milliDiff = hourDiff * HOURS_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone);
    }

    public int getHour() {
        return getCalendar().get(Calendar.HOUR_OF_DAY);
    }

    public TimePoint withMinute(int minute) {
        int minDiff = minute - getMinute();
        long milliDiff = minDiff * MINUTES_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone);
    }

    public int getMinute() {
        return getCalendar().get(Calendar.MINUTE);
    }

    public TimePoint withSecond(int second) {
        int secDiff = second - getSecond();
        long milliDiff = secDiff * SECONDS_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone);
    }

    public int getSecond() {
        return getCalendar().get(Calendar.SECOND);
    }

    public TimePoint addWeek() {
        return new TimePoint(unixTime + WEEKS_TO_MILLIS, timeZone);
    }

    public TimePoint addDay() {
        return new TimePoint(unixTime + DAYS_TO_MILLIS, timeZone);
    }

    /**
     * MATH METHODS
     **/

    public TimePoint plus(TimeDelta delta) {
        return new TimePoint(unixTime + delta.getDeltaLong(), timeZone);
    }

    public TimePoint minus(TimeDelta delta) {
        return new TimePoint(unixTime - delta.getDeltaLong(), timeZone);
    }

    public TimeDelta timeUntil(TimePoint other) {
        return new TimeDelta(other.getUnixTime() - getUnixTime());
    }

    /**
     * FIELD ACCESSORS
     **/

    public long getUnixTime() {
        return unixTime;
    }

    public boolean isBefore(TimePoint other) {
        return other.getUnixTime() > unixTime;
    }

    @Override
    public int hashCode() {
        int result = timeZone.hashCode();
        result = 31 * result + (int) (getUnixTime() ^ (getUnixTime() >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimePoint timePoint = (TimePoint) o;

        return getUnixTime() == timePoint.getUnixTime() && timeZone.equals(timePoint.timeZone);
    }

    @Override
    public int compareTo(TimePoint o) {
        return Long.compare(unixTime, o.getUnixTime());

    }
}
