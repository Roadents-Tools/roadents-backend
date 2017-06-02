package org.tymit.projectdonut.model.time;

/**
 * Created by ilan on 2/6/17.
 */
public class TimeDelta implements Comparable<TimeDelta> {

    public static final TimeDelta NULL = new TimeDelta(0);

    private final long delta;

    public TimeDelta(long time) {
        this.delta = time;
    }

    public TimeDelta plus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta + other.getDeltaLong());
    }

    public long getDeltaLong() {
        return delta;
    }

    public TimeDelta minus(TimeDelta other) {
        if (this == NULL) return other;
        if (other == null || other == NULL) return this;
        return new TimeDelta(delta - other.getDeltaLong());
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
