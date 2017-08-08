package com.reroute.backend.model.time;

/**
 * Created by ilan on 2/6/17.
 */
public class TimeDelta implements Comparable<TimeDelta> {

    public static final TimeDelta NULL = new TimeDelta(0);
    private static final double IN_SECONDS = 1000;
    private static final double IN_MINUTES = 60 * IN_SECONDS;
    private static final double IN_HOURS = 60 * IN_MINUTES;
    private static final double IN_DAYS = 24 * IN_HOURS;

    private final long delta;

    public TimeDelta(long time) {
        this.delta = time;
    }

    public TimeDelta plus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta + other.getDeltaLong());
    }

    public TimeDelta minus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta - other.getDeltaLong());
    }

    public TimeDelta mul(double multiplier) {
        long newDelta = (long) (multiplier * delta);
        if (newDelta == 0) return TimeDelta.NULL;
        return new TimeDelta(newDelta);
    }

    public TimeDelta div(double multiplier) {
        long newDelta = (long) (multiplier / delta);
        if (newDelta == 0) return TimeDelta.NULL;
        return new TimeDelta(newDelta);
    }

    public long getDeltaLong() {
        return delta;
    }

    public double inSeconds() {
        return delta / IN_SECONDS;
    }

    public double inMinutes() {
        return delta / IN_MINUTES;
    }

    public double inHours() {
        return delta / IN_HOURS;
    }

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
