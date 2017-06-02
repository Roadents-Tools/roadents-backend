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
    private static final long MONTHES_TO_MILLIS = 31 * DAYS_TO_MILLIS;
    private static final long YEARS_TO_MILLIS = 365 * DAYS_TO_MILLIS + DAYS_TO_MILLIS / 4;
    private static final long MIN_TIME = 31536000000L; //We don't allow for any time before 1971 for error checking

    private final String timeZone;
    private final long unixTime;

    /**
     * Creates a new TimePoint.
     *
     * @param unixTime the unix epoch time in milliseconds that this TimePoint represents. This value must be later
     *                 than January 1st, 1971, to prevent accidentally using seconds instead of milliseconds.
     * @param timeZone the timezone to use
     */
    public TimePoint(long unixTime, String timeZone) {
        if (unixTime < MIN_TIME)
            throw new IllegalArgumentException("Unixtime too low. Did you pass seconds instead of milliseconds?");
        this.unixTime = unixTime;
        this.timeZone = timeZone;
    }

    /**
     * Gets the year that this TimePoint represents.
     * @return the year component of this TimePoint
     */
    public int getYear() {
        return getCalendar().get(Calendar.YEAR);
    }

    private Calendar getCalendar() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(unixTime);
        return cal;
    }

    /**
     * Gets the month component of this TimePoint.
     * @return the month component of this TimePoint. The valid range is 1 for January to 12 for December.
     */
    public int getMonth() {
        return getCalendar().get(Calendar.MONTH) + 1;
    }

    /**
     * Creates a new TimePoint that represents the closest time from this TimePoint such that the day of the month
     * is dayOfMonth. This can be either before or after the original TimePoint, depending on when the nearest time is.
     * @param dayOfMonth the new day of the month to use
     * @return the new TimePoint
     */
    public TimePoint withDayOfMonth(int dayOfMonth) {
        if (dayOfMonth < 0 || dayOfMonth > 31) throw new IllegalArgumentException("Day of month invalid.");
        int dayDiff = dayOfMonth - getDayOfMonth();
        long millidiff = dayDiff * DAYS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone);
    }

    public int getDayOfMonth() {
        return getCalendar().get(Calendar.DAY_OF_MONTH);
    }

    public TimePoint withDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 0 || dayOfWeek > 7) throw new IllegalArgumentException("Day of week invalid.");
        int dayDiff = dayOfWeek - getDayOfWeek();
        long millidiff = dayDiff * DAYS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone);
    }

    public int getDayOfWeek() {
        //We go 0-6 instead of Calendar's 1-7
        return getCalendar().get(Calendar.DAY_OF_WEEK) - 1;
    }

    public TimePoint withHour(int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour invalid.");
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
        if (second < 0 || second > 60) throw new IllegalArgumentException("Second invalid.");
        int secDiff = second - getSecond();
        long milliDiff = secDiff * SECONDS_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone);
    }

    public int getSecond() {
        return getCalendar().get(Calendar.SECOND);
    }

    public TimePoint withMilliseconds(int milliseconds) {
        long millidiff = milliseconds - getMilliseconds();
        return new TimePoint(unixTime + millidiff, timeZone);
    }

    public long getMilliseconds() {
        return unixTime % 1000;
    }

    public TimePoint addWeek() {
        return new TimePoint(unixTime + WEEKS_TO_MILLIS, timeZone);
    }

    public TimePoint addDay() {
        return new TimePoint(unixTime + DAYS_TO_MILLIS, timeZone);
    }

    public TimePoint plus(TimeDelta delta) {
        return new TimePoint(unixTime + delta.getDeltaLong(), timeZone);
    }

    public TimePoint minus(TimeDelta delta) {
        return new TimePoint(unixTime - delta.getDeltaLong(), timeZone);
    }

    public TimeDelta timeUntil(TimePoint other) {
        return new TimeDelta(other.getUnixTime() - getUnixTime());
    }

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
