package com.reroute.backend.model.location;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.utils.LocationUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * A destination to a TravelRoute, or any location that is not a start
 * or a location to increase our range.
 * Created by ilan on 7/7/16.
 */
public class DestinationLocation implements LocationPoint {

    private static final Distance ERROR_MARGIN = new Distance(1, DistanceUnits.METERS);

    private final String name;
    private final LocationType type;
    private final double[] latLong;
    private final Optional<String> address;

    /**
     * Constructs a new destination.
     *
     * @param name    the name of the destination
     * @param type    the type of the destination
     * @param latLong the latitude and longitude of the destination
     */
    public DestinationLocation(String name, LocationType type, double[] latLong) {
        this(name, type, latLong, null);
    }

    /**
     * Constructs a new destination.
     * @param name the name of the destination
     * @param type the type of the destination
     * @param latLong the latitude and longitude of the destination
     * @param address the address of the destination
     */
    public DestinationLocation(String name, LocationType type, double[] latLong, String address) {
        this.name = name;
        this.type = type;
        this.latLong = latLong;
        this.address = Optional.ofNullable(address);
    }

    /**
     * Gets the name of this destination, ex "Panda Express" or "Startbucks".
     * @return the name of this destination
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets the type of this destination.
     * @return the type of this destination
     */
    @Override
    public LocationType getType() {
        return type;
    }

    /**
     * Gets the latitude and longitude of this destination.
     * @return an array containing, in order, the latitude and longitude of this destination
     */
    @Override
    public double[] getCoordinates() {
        return latLong;
    }

    /**
     * Gets the address of the destination.
     * @return an Optional containing the address
     */
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
