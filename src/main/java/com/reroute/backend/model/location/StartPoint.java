package com.reroute.backend.model.location;

import java.util.Arrays;

/**
 * A location that a route can start at.
 */
public class StartPoint implements LocationPoint {

    private static final String DEFAULT_NAME = "Start Point";
    private static final LocationType DEFAULT_TYPE = new LocationType("Start Point", "startpt");

    private final String name;
    private final LocationType type;
    private final double[] coords;

    /**
     * Constructs a new StartPoint at the location specified.
     *
     * @param latitude  the latitude of the point
     * @param longitude the longitude of the point
     */
    public StartPoint(double latitude, double longitude) {
        this(new double[] { latitude, longitude });
    }

    /**
     * Constructs a new StartPoint at the location specified.
     * @param coords the coordinates of the point, in [latitude, longitude] form
     */
    public StartPoint(double[] coords) {
        this(DEFAULT_NAME, DEFAULT_TYPE, coords);
    }

    /**
     * Constructs a new StartPoint.
     * @param name the name of the point
     * @param type the type of point this is
     * @param coords the coordinates of the start location
     */
    public StartPoint(String name, LocationType type, double[] coords) {
        this.name = name;
        this.type = type;
        this.coords = coords;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LocationType getType() {
        return type;
    }

    @Override
    public double[] getCoordinates() {
        return coords;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(coords);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StartPoint that = (StartPoint) o;

        return Arrays.equals(coords, that.coords);

    }

    @Override
    public String toString() {
        return "StartPoint{" +
                "name='" + name + '\'' +
                ", coords=" + Arrays.toString(coords) +
                '}';
    }
}
