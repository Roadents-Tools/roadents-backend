package org.tymit.projectdonut.locations.interfaces;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;

import java.util.List;

public interface LocationProvider {
    boolean isUsable();

    boolean isValidType(LocationType type);

    List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type);

    void close();
}
