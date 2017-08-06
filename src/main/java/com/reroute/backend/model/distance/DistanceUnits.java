package com.reroute.backend.model.distance;

public enum DistanceUnits {

    METERS(1),
    KILOMETERS(1000),
    MILES(1609.344),
    FEET(.3048);

    public final double toMeters;

    DistanceUnits(double meters) {
        this.toMeters = meters;
    }
}
