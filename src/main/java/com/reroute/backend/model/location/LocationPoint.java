package com.reroute.backend.model.location;


public interface LocationPoint {
    String getName();

    LocationType getType();

    double[] getCoordinates();
}
