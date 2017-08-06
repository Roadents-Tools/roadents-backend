package com.reroute.backend.model.distance;

public final class Distance {

    private final double meters;

    public Distance(double range, DistanceUnits unit) {
        this.meters = range * unit.toMeters;
    }

    public double inMiles() {
        return meters / DistanceUnits.MILES.toMeters;
    }

    public double inFeet() {
        return meters / DistanceUnits.FEET.toMeters;
    }

    public double inKilometers() {
        return meters / DistanceUnits.KILOMETERS.toMeters;
    }

    /**
     * Math methods
     **/

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

    /**
     * Conversion methods
     **/

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

        return Double.compare(distance.meters, meters) == 0;
    }

    @Override
    public String toString() {
        return "Distance{" +
                "meters=" + meters +
                '}';
    }
}
