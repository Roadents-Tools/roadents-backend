package org.tymit.projectdonut.locations.interfaces;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

import java.util.List;

public interface LocationProvider {
    boolean isUsable();

    boolean isValidType(LocationType type);

    List<DestinationLocation> queryLocations(double[] center, double range, LocationType type);
}
