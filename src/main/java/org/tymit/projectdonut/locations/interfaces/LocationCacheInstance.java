package org.tymit.projectdonut.locations.interfaces;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;

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
