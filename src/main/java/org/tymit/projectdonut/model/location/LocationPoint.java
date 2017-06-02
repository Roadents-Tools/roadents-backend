package org.tymit.projectdonut.model.location;


public interface LocationPoint {
    String getName();

    LocationType getType();

    double[] getCoordinates();
}
