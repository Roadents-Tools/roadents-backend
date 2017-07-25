package org.tymit.projectdonut.stations.memory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Collections;
import java.util.List;

public class ChainStore {

    private static final int MAX_SIZE = 1000;
    private static final String TAG = "ChainStore";

    private static ChainStore instance = new ChainStore();

    private LoadingCache<String, List<TransStation>> chaincache = CacheBuilder.newBuilder()
            .maximumSize(MAX_SIZE)
            .concurrencyLevel(2)
            .build(CacheLoader.from(a -> Collections.emptyList()));

    private ChainStore() {
    }

    public static ChainStore getStore() {
        return instance;
    }

    public List<TransStation> getChain(String chainName) {
        return chaincache.getUnchecked(chainName);
    }

    public void putChain(String chainName, List<TransStation> stations) {
        if (chainName == null || stations == null || stations.isEmpty()) {
            LoggingUtils.logError(TAG, "Tried storing null args.");
            return;
        }

        if (stations.stream()
                .map(TransStation::getChain)
                .map(TransChain::getName)
                .anyMatch(a -> !a.equals(chainName))) {
            LoggingUtils.logError(TAG, "Tried storing bad stations.");
            return;
        }

        chaincache.put(chainName, stations);

    }

    public void putChain(TransChain chain, List<TransStation> stations) {

        if (chain == null || stations == null || stations.isEmpty()) {
            LoggingUtils.logError(TAG, "Tried storing null args.");
            return;
        }

        if (stations.stream().map(TransStation::getChain).anyMatch(a -> !a.equals(chain))) {
            LoggingUtils.logError(TAG, "Tried storing bad stations.");
            return;
        }

        chaincache.put(chain.getName(), stations);
    }
}
