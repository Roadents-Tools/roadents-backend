package org.tymit.projectdonut.locations.providers;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;

import java.util.List;

public interface LocationProvider {
    boolean isUsable();

    boolean isValidType(LocationType type);

    List<DestinationLocation> queryLocations(double[] center, double range, LocationType type);
}
