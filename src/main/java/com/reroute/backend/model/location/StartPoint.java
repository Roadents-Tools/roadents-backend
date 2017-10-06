package com.reroute.backend.model.location;

import java.util.Arrays;

/**
 * Created by ilan on 7/10/16.
 */
public class StartPoint implements LocationPoint {

    private static final String DEFAULT_NAME = "Start Point";
    private static final LocationType DEFAULT_TYPE = new LocationType("Start Point", "startpt");

    private final String name;
    private final LocationType type;
    private final double[] coords;

    public StartPoint(double latitude, double longitude) {
        this(new double[] { latitude, longitude });
    }

    public StartPoint(double[] coords) {
        this(DEFAULT_NAME, DEFAULT_TYPE, coords);
    }

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
