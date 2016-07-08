package org.tymit.projectdonut.model;

/**
 * Created by ilan on 7/7/16.
 */
public class TransStation implements LocationPoint {

    private double[] location;
    private LocationType type = new LocationType("Station", "TransStation");
    private String name;

    public TransStation(double[] location, LocationType type, String name) {
        this.location = location;
        this.type = type;
        this.name = name;
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
        return location;
    }
}
