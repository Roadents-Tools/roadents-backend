package com.reroute.backend.locations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.List;

public interface LocationProvider {
    boolean isUsable();

    boolean isValidType(LocationType type);

    List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type);

    void close();
}
