package org.tymit.projectdonut.model.location;

import java.util.Arrays;

/**
 * Created by ilan on 7/7/16.
 */
public class DestinationLocation implements LocationPoint {

    private static final double ERROR_MARGIN = 0.001;

    private final String name;
    private final LocationType type;
    private final double[] latLong;

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

    @Override
    public int hashCode() {
        int result = getType().hashCode();
        result = 31 * result + Arrays.hashCode(latLong);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DestinationLocation location = (DestinationLocation) o;

        if (!type.equals(location.type)) return false;
        if (Math.abs(location.getCoordinates()[0] - getCoordinates()[0]) > ERROR_MARGIN) return false;
        return Math.abs(location.getCoordinates()[1] - getCoordinates()[1]) <= ERROR_MARGIN;

    }

    @Override
    public String toString() {
        return "DestinationLocation{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", latLong=" + Arrays.toString(latLong) +
                '}';
    }
}
