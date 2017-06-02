package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface LocationCacheInstance {
    void cacheLocations(double[] center, double range, LocationType type, List<DestinationLocation> locations);


    List<DestinationLocation> getCachedLocations(double[] center, double range, LocationType type);

    int getSize();

    void clear();
}
