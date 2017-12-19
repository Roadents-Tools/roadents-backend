package com.reroute.backend.model.location;

/**
 * The type of a location.
 */
public class LocationType {
    private final String visibleName;
    private final String encodedName;

    /**
     * Constructs a new type.
     *
     * @param visibleName the human readable name to display for this type
     * @param encodedName the name of the type for the computer to use for hashing and equality checking
     */
    public LocationType(String visibleName, String encodedName) {
        this.visibleName = visibleName;
        this.encodedName = encodedName;
    }

    @Override
    public int hashCode() {
        int result = getVisibleName().hashCode();
        result = 31 * result + encodedName.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LocationType
                && ((LocationType) o).getEncodedname().equals(this.getEncodedname());
    }

    @Override
    public String toString() {
        return "LocationType{" +
                "visibleName='" + visibleName + '\'' +
                ", encodedName='" + encodedName + '\'' +
                '}';
    }

    /**
     * Gets the encoded name of this type.
     * @return the encoded name of this type
     */
    public String getEncodedname() {
        return encodedName;
    }

    /**
     * Gets the visible name of this type.
     * @return the visible name of this type
     */
    public String getVisibleName() {
        return visibleName;
    }
}
