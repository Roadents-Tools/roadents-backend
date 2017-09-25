package com.reroute.backend.model.distance;

public final class Distance {

    public static final Distance NULL = new Distance(0, DistanceUnits.METERS);

    public static final Distance ERROR_MARGIN = new Distance(.1, DistanceUnits.METERS);

    private final double meters;

    public Distance(double range, DistanceUnits unit) {
        this.meters = range * unit.toMeters;
    }

    public double inMiles() {
        return inMeters() / DistanceUnits.MILES.toMeters;
    }

    public double inFeet() {
        return inMeters() / DistanceUnits.FEET.toMeters;
    }

    public double inKilometers() {
        return inMeters() / DistanceUnits.KILOMETERS.toMeters;
    }

    public Distance plus(Distance other) {
        return new Distance(this.meters + other.meters, DistanceUnits.METERS);
    }

    public Distance minus(Distance other) {
        return new Distance(this.meters - other.meters, DistanceUnits.METERS);
    }

    public Distance mul(double factor) {
        return new Distance(this.meters * factor, DistanceUnits.METERS);
    }

    public Distance div(double divisor) {
        return new Distance(this.meters / divisor, DistanceUnits.METERS);
    }

    public boolean deltaEqual(Distance other, Distance delta) {
        double deltaMeters = delta.inMeters();
        double diffMeters = this.inMeters() - other.inMeters();
        return Math.abs(diffMeters) <= deltaMeters;
    }

    public boolean isZero() {
        return this.deltaEqual(NULL, ERROR_MARGIN);
    }

    public double inMeters() {
        return meters;
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
