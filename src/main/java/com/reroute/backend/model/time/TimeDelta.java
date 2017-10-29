package com.reroute.backend.model.time;

/**
 * A difference of time.
 * Created by ilan on 2/6/17.
 */
public class TimeDelta implements Comparable<TimeDelta> {

    /**
     * Represents an empty difference of 0.
     */
    public static final TimeDelta NULL = new TimeDelta(0);
    private static final double IN_SECONDS = 1000;
    private static final double IN_MINUTES = 60 * IN_SECONDS;
    private static final double IN_HOURS = 60 * IN_MINUTES;
    private static final double IN_DAYS = 24 * IN_HOURS;

    private final long delta;

    /**
     * Constructs a new TimeDelta.
     *
     * @param time the length of the delta in milliseconds
     */
    public TimeDelta(long time) {
        this.delta = time;
    }

    /**
     * Gets the sum between 2 deltas.
     * @param other the delta to add
     * @return the delta whose duration is this + other
     */
    public TimeDelta plus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta + other.getDeltaLong());
    }

    /**
     * Gets this delta in milliseconds.
     *
     * @return this delta in milliseconds
     */
    public long getDeltaLong() {
        return delta;
    }

    /**
     * Gets the difference between 2 deltas.
     * @param other the delta to subtract
     * @return the delta whose duration is this - other
     */
    public TimeDelta minus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta - other.getDeltaLong());
    }

    /**
     * Multiplies a delta.
     * @param multiplier the amount to multiply
     * @return the delta whose duration is this * multiplier
     */
    public TimeDelta mul(double multiplier) {
        long newDelta = (long) (multiplier * delta);
        if (newDelta == 0) return TimeDelta.NULL;
        return new TimeDelta(newDelta);
    }

    /**
     * Divides a delta.
     *
     * @param divisor the amount to divide
     * @return the delta whose duration is this / divisor
     */
    public TimeDelta div(double divisor) {
        long newDelta = (long) (divisor / delta);
        if (newDelta == 0) return TimeDelta.NULL;
        return new TimeDelta(newDelta);
    }

    /**
     * Gets this delta in seconds.
     * @return this delta in seconds
     */
    public double inSeconds() {
        return delta / IN_SECONDS;
    }

    /**
     * Gets this delta in minutes.
     * @return this delta in minutes
     */
    public double inMinutes() {
        return delta / IN_MINUTES;
    }

    /**
     * Gets this delta in hours.
     * @return this delta in hours
     */
    public double inHours() {
        return delta / IN_HOURS;
    }

    /**
     * Gets this delta in days.
     * @return this delta in days
     */
    public double inDays() {
        return delta / IN_DAYS;
    }

    @Override
    public int hashCode() {
        return (int) (delta ^ (delta >>> 32));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeDelta timeDelta = (TimeDelta) o;

        return delta == timeDelta.delta;
    }

    @Override
    public String toString() {
        return "TimeDelta{" +
                "delta=" + delta +
                '}';
    }


    @Override
    public int compareTo(TimeDelta o) {
        return Long.compare(getDeltaLong(), o.getDeltaLong());
    }
}
