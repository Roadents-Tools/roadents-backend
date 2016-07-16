package org.tymit.projectdonut.model;

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
}
