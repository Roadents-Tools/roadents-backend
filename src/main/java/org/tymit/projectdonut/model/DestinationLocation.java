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
    public boolean equals(Object o) {
        return o instanceof DestinationLocation
                && ((DestinationLocation) o).getCoordinates()[0] == this.getCoordinates()[0]
                && ((DestinationLocation) o).getCoordinates()[1] == this.getCoordinates()[1]
                && ((DestinationLocation) o).getName().equals(this.getName())
                && ((DestinationLocation) o).getType().equals(this.getType());
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
