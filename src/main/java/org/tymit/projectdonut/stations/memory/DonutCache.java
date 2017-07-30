package org.tymit.projectdonut.stations.memory;

import ch.hsr.geohash.GeoHash;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationCacheInstance;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DonutCache implements StationCacheInstance.DonutCache {

    private static final int HASH_BITS = 50;

    private Map<String, List<TransChain>> chainCache = new HashMap<>();
    private Map<String, List<TransStation>> stationCache = new HashMap<>();


    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, double range) {
        //TODO
        return null;
    }

    @Override
    public List<TransChain> getChainsForStation(TransStation station) {
        GeoHash stationHash = GeoHash.withBitPrecision(station.getCoordinates()[0], station.getCoordinates()[1], HASH_BITS);
        String hashString = stationHash.toString();
        return chainCache.getOrDefault(hashString, Collections.emptyList());
    }

    @Override
    public List<TransStation> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        TimePoint endTime = startTime.plus(maxDelta);
        return stationCache.getOrDefault(chain.getName(), Collections.emptyList()).stream()
                .filter(st -> {
                    long nextArrival = st.getNextArrival(startTime).getUnixTime();
                    return nextArrival >= startTime.getUnixTime() && nextArrival <= endTime.getUnixTime();
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean putArea(LocationPoint center, double range, List<TransStation> stations) {
        return false;
    }

    @Override
    public boolean putChainsForStation(TransStation station, List<TransChain> chains) {
        GeoHash stationHash = GeoHash.withBitPrecision(station.getCoordinates()[0], station.getCoordinates()[1], HASH_BITS);
        String hashString = stationHash.toString();
        return chainCache.putIfAbsent(hashString, chains) != null;
    }

    @Override
    public boolean putStationsForChain(TransChain chain, List<TransStation> station) {
        String name = chain.getName();
        return stationCache.putIfAbsent(name, station) != null;
    }

    @Override
    public void close() {
        chainCache.clear();
        stationCache.clear();
    }


}
