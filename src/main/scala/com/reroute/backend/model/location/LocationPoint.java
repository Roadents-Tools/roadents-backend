package com.reroute.backend.model.location;

/**
 * A physical location on earth.
 */
public interface LocationPoint {

    /**
     * Gets the name of the location.
     *
     * @return the name of the location
     */
    String getName();

    /**
     * Gets the type of the location.
     * @return the type of the location
     */
    LocationType getType();

    /**
     * Gets the physical coordinates of this location.
     * The standard is [latitude, longitude].
     * @return the location of this point
     */
    double[] getCoordinates();
}
