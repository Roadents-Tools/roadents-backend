package org.tymit.projectdonut.model;

/**
 * Created by ilan on 7/7/16.
 */
public class DestinationLocation implements LocationPoint {

    private String name;
    private LocationType type;
    private double[] latLong;

    public DestinationLocation(String name, LocationType type, double[] latLong) {
        this.name = name;
        this.type = type;
        this.latLong = latLong;
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
        return latLong;
    }
}
