package org.tymit.projectdonut.model;


public interface LocationPoint {
    String getName();

    LocationType getType();

    double[] getCoordinates();
}
