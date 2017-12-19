package com.reroute.backend.model.distance;

/**
 * A spacial distance between points.
 */
public final class Distance {

    /**
     * A singleton distance to represent an "empty" distance of 0 meters/feet/inches etc. Useful for optionals, invalids,
     * etc.
     */
    public static final Distance NULL = new Distance(0, DistanceUnits.METERS);

    /**
     * The standard distance to use to check if 2 Distance objects are the same, equal to 100 cm.
     */
    public static final Distance ERROR_MARGIN = new Distance(.1, DistanceUnits.METERS);

    private final double meters;

    /**
     * Constructs a new distance.
     *
     * @param range the distance to use
     * @param unit  the units that the range is in
     */
    public Distance(double range, DistanceUnits unit) {
        this.meters = range * unit.toMeters;
    }

    /**
     * Gets this distance in miles.
     * @return this distance in miles
     */
    public double inMiles() {
        return inMeters() / DistanceUnits.MILES.toMeters;
    }

    /**
     * Gets this distance in meters.
     *
     * @return this distance in meters
     */
    public double inMeters() {
        return meters;
    }

    /**
     * Gets this distance in feet.
     * @return this distance in feet
     */
    public double inFeet() {
        return inMeters() / DistanceUnits.FEET.toMeters;
    }

    /**
     * Gets this distance in kilometers.
     * @return this distance in kilometers
     */
    public double inKilometers() {
        return inMeters() / DistanceUnits.KILOMETERS.toMeters;
    }

    /**
     * Adds 2 distances together.
     * @param other the other distance to add
     * @return a new distance whose total length is our length + other's length
     */
    public Distance plus(Distance other) {
        return new Distance(this.meters + other.meters, DistanceUnits.METERS);
    }

    /**
     * Subtracts 2 distances.
     * @param other the distance to subtract
     * @return a new distance whose total length is ours - other's
     */
    public Distance minus(Distance other) {
        return new Distance(this.meters - other.meters, DistanceUnits.METERS);
    }

    /**
     * Multiplies a distance.
     * @param factor the factor to scale the distance by
     * @return a new distance whose length is ours * factor
     */
    public Distance mul(double factor) {
        return new Distance(this.meters * factor, DistanceUnits.METERS);
    }

    /**
     * Divides a distance.
     * @param divisor the number to scale down by
     * @return a new distance whose length is ours / divisor
     */
    public Distance div(double divisor) {
        return new Distance(this.meters / divisor, DistanceUnits.METERS);
    }

    /**
     * Checks whether this distance is empty. Equivalent to this.deltaEquals(Distance.NULL, Distance.ERROR_MARGIN).
     *
     * @return whether this distance is empty
     */
    public boolean isZero() {
        return this.deltaEqual(NULL, ERROR_MARGIN);
    }

    /**
     * Checks whether 2 distances are close to each other, with a given margin.
     * @param other the distance to compare this to
     * @param delta the margin to use
     * @return whether the difference between this and other is less than delta
     */
    public boolean deltaEqual(Distance other, Distance delta) {
        double deltaMeters = delta.inMeters();
        double diffMeters = this.inMeters() - other.inMeters();
        return Math.abs(diffMeters) <= deltaMeters;
    }

    @Override
    public int hashCode() {
        long temp = Double.doubleToLongBits(meters);
        return (int) (temp ^ (temp >>> 32));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Distance distance = (Distance) o;

        return this.deltaEqual(distance, ERROR_MARGIN);
    }

    @Override
    public String toString() {
        return "Distance{" +
                "meters=" + meters +
                '}';
    }
}
