package org.tymit.projectdonut.stations.memory;

import ch.hsr.geohash.GeoHash;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.tymit.projectdonut.model.location.TransStation;

import java.util.Collections;
import java.util.List;

public class StationToChainStore {

    private static final int MAX_SIZE = 1000;
    private static final String TAG = "StationStore";
    private static StationToChainStore instance = new StationToChainStore();
    private LoadingCache<StationKey, List<TransStation>> stationcache = CacheBuilder.newBuilder()
            .maximumSize(MAX_SIZE)
            .concurrencyLevel(2)
            .build(CacheLoader.from(a -> Collections.emptyList()));

    private StationToChainStore() {
    }

    public static StationToChainStore getInstance() {
        return instance;
    }

    public List<TransStation> getChainsForStation(double lat, double lng) {
        return stationcache.getUnchecked(new StationKey(lat, lng));
    }

    public void putStation(double lat, double lng, List<TransStation> chainsForStations) {
        StationKey key = new StationKey(lat, lng);
        stationcache.put(key, chainsForStations);
    }


    private static class StationKey {
        private GeoHash hash;

        public StationKey(double lat, double lng) {
            hash = GeoHash.withBitPrecision(lat, lng, 50);
        }

        @Override
        public int hashCode() {
            return hash != null ? hash.hashCode() : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StationKey that = (StationKey) o;

            return hash != null ? hash.equals(that.hash) : that.hash == null;
        }
    }
}
