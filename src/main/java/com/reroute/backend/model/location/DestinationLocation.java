package com.reroute.backend.model.location;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.utils.LocationUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * Created by ilan on 7/7/16.
 */
public class DestinationLocation implements LocationPoint {

    private static final Distance ERROR_MARGIN = new Distance(1, DistanceUnits.METERS);

    private final String name;
    private final LocationType type;
    private final double[] latLong;
    private final Optional<String> address;

    public DestinationLocation(String name, LocationType type, double[] latLong) {
        this(name, type, latLong, null);
    }

    public DestinationLocation(String name, LocationType type, double[] latLong, String address) {
        this.name = name;
        this.type = type;
        this.latLong = latLong;
        this.address = Optional.ofNullable(address);
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

    public Optional<String> getAddress() {
        return address;
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
        Distance margin = LocationUtils.distanceBetween(this, location);
        return !(margin.inMeters() > ERROR_MARGIN.inMeters());
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
