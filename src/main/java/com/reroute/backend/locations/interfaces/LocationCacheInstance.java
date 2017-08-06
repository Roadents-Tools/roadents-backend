package com.reroute.backend.locations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface LocationCacheInstance {
    void cacheLocations(LocationPoint center, Distance range, LocationType type, List<DestinationLocation> locations);

    List<DestinationLocation> getCachedLocations(LocationPoint center, Distance range, LocationType type);

    int getSize();

    void clear();

    void close();
}
