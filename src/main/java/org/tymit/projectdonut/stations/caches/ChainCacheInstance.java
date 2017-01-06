package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 1/4/17.
 */
public interface ChainCacheInstance {

    void cacheChain(TransChain chain, List<TransStation> stations);

    List<TransStation> getCachedStations(TransChain chain);

    int getSize();

    void clear();
}
