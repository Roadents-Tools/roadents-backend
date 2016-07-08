package org.tymit.projectdonut.locations.providers;

import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;

import java.util.List;

public interface LocationProvider {
    boolean isUsable();

    boolean isValidType(LocationType type);

    List<LocationPoint> queryLocations(double[] center, double range, LocationType type);
}
