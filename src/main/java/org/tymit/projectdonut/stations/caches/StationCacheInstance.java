package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {
    void cacheStations(double[] center, double range, List<TransStation> stations);


    List<TransStation> getCachedStations(double[] center, double range);

    int getSize();

    void clear();
}
