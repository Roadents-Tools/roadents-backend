package com.reroute.backend.model.time;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * A single point in time with a timezone.
 * Created by ilan on 2/6/17.
 */
public class TimePoint implements Comparable<TimePoint> {

    public static final TimePoint NULL = new TimePoint(0, "GMT", true);
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
    private final long offset;
    private Calendar cal = null;

    /**
     * Creates a new TimePoint.
     *
     * @param unixTime the unix epoch time in milliseconds that this TimePoint represents. This value must be later
     *                 than January 1st, 1971, to prevent accidentally using seconds instead of milliseconds.
     * @param timeZone the timezone to use
     */
    public TimePoint(long unixTime, String timeZone) {
        this(unixTime, timeZone, false);
    }

    private TimePoint(long unixTime, String timeZone, boolean allowUnderflow) {
        this(unixTime, timeZone, TimeZone.getTimeZone(timeZone).getOffset(unixTime), allowUnderflow);
    }

    private TimePoint(long unixTime, String timeZone, long offset, boolean allowUnderflow) {
        if (unixTime < MIN_TIME && unixTime > 0 && !allowUnderflow)
            throw new IllegalArgumentException("Unixtime too low. Did you pass seconds instead of milliseconds?");
        this.unixTime = unixTime;
        this.timeZone = timeZone;
        this.offset = offset;
    }

    /**
     * Gets the current time. By default uses the GMT timezone.
     *
     * @return the current time in the GMT timezone
     */
    public static TimePoint now() {
        return now("GMT");
    }

    /**
     * Gets the current time.
     * @param timeZone the timezone to use
     * @return a TimePoint representing the current time at the specified timezone
     */
    public static TimePoint now(String timeZone) {
        return new TimePoint(System.currentTimeMillis(), timeZone);
    }

    private Calendar getCalendar() {
        if (cal == null) {
            cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
            cal.setTimeInMillis(unixTime);
        }
        return cal;
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
        return new TimePoint(unixTime + millidiff, timeZone, offset, true);
    }

    /**
     * Gets the day of the month this TimePoint represents.
     * @return the day of the month, from 1 to 31.
     */
    public int getDayOfMonth() {
        return getCalendar().get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Creates a new TimeZone with the day of the week equal to the one specified, and all other time attributes being
     * the same.
     * NOTE: Currently, the new TimePoint is not guaranteed to have the save time information as the base. In a daylight
     * savings time case the TimePoint might be an hour off.
     * @param dayOfWeek the new day of the week, from 0 (Sunday) to 6 (Saturday)
     * @return a new TimeZone whose day of the week is the one specified
     */
    public TimePoint withDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 0 || dayOfWeek > 7) throw new IllegalArgumentException("Day of week invalid.");
        int dayDiff = dayOfWeek - getDayOfWeek();
        long millidiff = dayDiff * DAYS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone, offset, true);
    }

    /**
     * Returns the day of the week of this TimePoint.
     * @return the day of the week, from 0 (Sunday) to 6 (Saturday)
     */
    public int getDayOfWeek() {
        //We go 0-6 instead of Calendar's 1-7
        return (int) ((((unixTime + offset) / DAYS_TO_MILLIS) + 3) % 7);
    }

    public TimePoint withTime(int packedTime) {
        if (packedTime < 0 || packedTime >= 86400) throw new IllegalArgumentException("Packed time invalid.");
        int seconddiff = packedTime - getPackedTime();
        long millidiff = seconddiff * SECONDS_TO_MILLIS;
        return new TimePoint(unixTime + millidiff, timeZone, offset, true);
    }

    public int getPackedTime() {
        //Convert to local time by adding offset, mod by DAYS_TO_MILLIS to get the millis since midnight,
        //divide by 1000 to get the seconds.
        return (int) (((unixTime + offset) % DAYS_TO_MILLIS) / 1000);
    }

    /**
     * Construct a new TimePoint whose hour is the one specified.
     * @param hour the hour value of the new TimePoint, from 0 to 23
     * @return the new TimePoint
     */
    public TimePoint withHour(int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour invalid.");
        int hourDiff = hour - getHour();
        long milliDiff = hourDiff * HOURS_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone, offset, true);
    }

    /**
     * Gets the hour of this TimePoint.
     * @return the hour of this TimePoint, from 0 to 23.
     */
    public int getHour() {
        return (int) (((unixTime + offset) / 3600000) % 24);
    }

    /**
     * Constructs a new TimePoint whose minute is the one specified.
     * @param minute the new minute, from 0 to 59.
     * @return the new TimePoint with the specified minute
     */
    public TimePoint withMinute(int minute) {
        int minDiff = minute - getMinute();
        long milliDiff = minDiff * MINUTES_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone, offset, true);
    }

    /**
     * Gets the minute of this TimePoint.
     * @return the minute of this TimePoint, from 0 to 59.
     */
    public int getMinute() {
        return (int) ((unixTime / (60000.0)) % 60);
    }

    /**
     * Construct a new TimePoint whose seconds is the one specified.
     * @param second the seconds value of the new TimePoint, from 0 to 59
     * @return the new TimePoint
     */
    public TimePoint withSecond(int second) {
        if (second < 0 || second > 60) throw new IllegalArgumentException("Second invalid.");
        int secDiff = second - getSecond();
        long milliDiff = secDiff * SECONDS_TO_MILLIS;
        return new TimePoint(unixTime + milliDiff, timeZone, offset, true);
    }

    /**
     * Gets the seconds of this TimePoint.
     * @return the seconds of this TimePoint, from 0 to 59.
     */
    public int getSecond() {
        return (int) ((unixTime / SECONDS_TO_MILLIS) % 60);
    }

    /**
     * Constructs a new TimePoint whose milliseconds is the one specified.
     * @param milliseconds the new milliseconds, from 0 to 999
     * @return the new TimePoint with the specified milliseconds
     */
    public TimePoint withMilliseconds(int milliseconds) {
        long millidiff = milliseconds - getMilliseconds();
        return new TimePoint(unixTime + millidiff, timeZone, offset, true);
    }

    /**
     * Gets the milliseconds of this TimePoint.
     * @return the milliseconds of this TimePoint, from 0 to 999
     */
    public long getMilliseconds() {
        return unixTime % SECONDS_TO_MILLIS;
    }

    /**
     * Adds exactly 7 days worth of time to this TimePoint.
     * @return a new TimePoint whose time is this + 168 hours
     */
    public TimePoint addWeek() {
        return new TimePoint(unixTime + WEEKS_TO_MILLIS, timeZone, offset, true);
    }

    /**
     * Adds exactly 1 days worth time to this TimePoint.
     * @return a new TimePoint whose time is this + 24 hours
     */
    public TimePoint addDay() {
        return new TimePoint(unixTime + DAYS_TO_MILLIS, timeZone, offset, true);
    }

    /**
     * Adds time to this TimePoint.
     * @param delta the time to add
     * @return a new TimePoint representing the time this + delta
     */
    public TimePoint plus(TimeDelta delta) {
        return new TimePoint(unixTime + delta.getDeltaLong(), timeZone, offset, true);
    }

    /**
     * Subtracts time to this TimePoint.
     * @param delta the time to subtract
     * @return a new TimePoint representing the time this - delta
     */
    public TimePoint minus(TimeDelta delta) {
        return new TimePoint(unixTime - delta.getDeltaLong(), timeZone, offset, true);
    }

    /**
     * Gets the time difference between 2 TimePoints.
     * @param other the other TimePoint to compare
     * @return the time from this to other
     */
    public TimeDelta timeUntil(TimePoint other) {
        return new TimeDelta(other.getUnixTime() - getUnixTime());
    }

    /**
     * Gets the unix epoch time of this TimePoint.
     * @return the number of milliseconds between January 1, 1970, 0000:0000 and this TimePoint.
     */
    public long getUnixTime() {
        return unixTime;
    }

    /**
     * Gets the TimeZone of this TimePoint.
     * @return the string name of thise TimeZone, eg "America/New_York".
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * Constructs a new TimePoint with the same time as this but a different timezone.
     * @param timeZone the new timezone
     * @return a new TimePoint in the specified timezone
     */
    public TimePoint withTimeZone(String timeZone) {
        return new TimePoint(unixTime, timeZone);
    }

    /**
     * Gets whether this TimeZone is before another.
     * @param other the timezone to compare
     * @return whether this is before other
     */
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
    public String toString() {
        String dateTimeString = String.format("%d-%d-%d %d:%d:%d:%d", getYear(), getMonth(), getDayOfMonth(), getHour(), getMinute(), getSecond(), getMilliseconds());
        return "TimePoint{ " +
                "datetime=" + dateTimeString + ", " +
                "unixTime=" + unixTime + ", " +
                "timeZone='" + timeZone + '\'' +
                '}';
    }

    /**
     * Gets the year that this TimePoint represents.
     *
     * @return the year component of this TimePoint
     */
    public int getYear() {
        return getCalendar().get(Calendar.YEAR);
    }

    /**
     * Gets the month component of this TimePoint.
     *
     * @return the month component of this TimePoint. The valid range is 1 for January to 12 for December.
     */
    public int getMonth() {
        return getCalendar().get(Calendar.MONTH) + 1;
    }

    @Override
    public int compareTo(TimePoint o) {
        return Long.compare(unixTime, o.getUnixTime());

    }
}
