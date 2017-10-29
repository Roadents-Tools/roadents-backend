package com.reroute.backend.model.distance;

/**
 * Different units of distance.
 */
public enum DistanceUnits {

    METERS(1),
    KILOMETERS(1000),
    MILES(1609.344),
    FEET(.3048);

    /**
     * How many meters could fit in 1 of these units. For example, DistanceUnits.KILOMETERS.toMeters = 1000.
     */
    public final double toMeters;

    /**
     * Constructs a new DistanceUnit.
     *
     * @param meters the value of toMeters
     */
    DistanceUnits(double meters) {
        this.toMeters = meters;
    }
}
