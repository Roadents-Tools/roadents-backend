package org.tymit.projectdonut.locations.caches;

import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface LocationCacheInstance {
    void cacheLocations(double[] center, double range, LocationType type, List<LocationPoint> locations);


    List<LocationPoint> getCachedLocations(double[] center, double range, LocationType type);

    int getSize();

    void clear();
}
