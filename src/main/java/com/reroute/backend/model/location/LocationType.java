package com.reroute.backend.model.location;

public class LocationType {
    private final String visibleName;
    private final String encodedName;

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
                && ((LocationType) o).getVisibleName().equals(this.getVisibleName())
                && ((LocationType) o).getEncodedname().equals(this.getEncodedname());
    }

    @Override
    public String toString() {
        return "LocationType{" +
                "visibleName='" + visibleName + '\'' +
                ", encodedName='" + encodedName + '\'' +
                '}';
    }

    public String getEncodedname() {
        return encodedName;
    }

    public String getVisibleName() {
        return visibleName;
    }
}
