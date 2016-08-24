package org.tymit.projectdonut.model;

import java.util.Arrays;

/**
 * Created by ilan on 7/10/16.
 */
public class StartPoint implements LocationPoint {

    private String name = "Start Point";

    private LocationType type = new LocationType("Start Point", "startpt");

    private double[] coords;

    public StartPoint(double[] coords) {
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
