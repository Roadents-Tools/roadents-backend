package org.tymit.projectdonut.stations.caches;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 1/4/17.
 */
public class MemoryMapChainCache implements ChainCacheInstance {

    private static final int MAX_TOTAL_SIZE = 100000;

    Map<TransChain, List<TransStation>> cache = new ConcurrentHashMap<>();
    int size = 0;
    Random rng = new Random();

    @Override
    public void cacheChain(TransChain chain, List<TransStation> stations) {
        if (chain == null || chain.getStations() == null || chain.getStations().isEmpty()) return;
        cache.put(chain, stations);
        size += stations.size();
        trimToSize();
    }

    @Override
    public List<TransStation> getCachedStations(TransChain chain) {
        return cache.get(chain);
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void clear() {
        cache.clear();
        size = 0;
    }

    private void trimToSize() {
        List<TransChain> keys = new ArrayList<>(cache.keySet());
        while (size > MAX_TOTAL_SIZE) {
            TransChain toDel = keys.get(rng.nextInt(keys.size()));
            List<TransStation> toDelVals = cache.get(toDel);

            cache.remove(toDel);
            size -= toDelVals.size();
        }

    }
}
