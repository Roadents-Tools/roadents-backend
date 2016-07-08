package org.tymit.projectdonut.model;

public class LocationType {
    private final String visibleName;
    private final String encodedName;

    public LocationType(String visibleName, String encodedName) {
        this.visibleName = visibleName;
        this.encodedName = encodedName;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LocationType
                && ((LocationType) o).getVisibleName().equals(this.getVisibleName())
                && ((LocationType) o).getEncodedname().equals(this.getEncodedname());
    }

    public String getVisibleName() {
        return visibleName;
    }

    public String getEncodedname() {
        return encodedName;
    }
}
