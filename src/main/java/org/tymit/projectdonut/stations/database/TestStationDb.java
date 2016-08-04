package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/10/16.
 */
public class TestStationDb implements StationDbInstance {

    private static final TransChain NULL_CHAIN = new TransChain("NULLCHAINNAME");
    private static Map<TransChain, List<TransStation>> chainsToStations = new ConcurrentHashMap<>();

    public static void setTestStations(Collection<TransStation> testStations) {
        chainsToStations.clear();
        if (testStations == null) return;
        testStations.forEach(station -> {
            TransChain stationChain = station.getChain();
            if (stationChain == null) stationChain = NULL_CHAIN;
            chainsToStations.putIfAbsent(stationChain, new ArrayList<>());
            chainsToStations.get(stationChain).add(station);
        });
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {
        List<TransStation> stationsToCheck = (chain != null) ? chainsToStations.get(chain) : null;
        if (stationsToCheck == null) {
            stationsToCheck = new ArrayList<>();
            chainsToStations.values().forEach(stationsToCheck::addAll);
        }
        return stationsToCheck.parallelStream()
                .filter(station -> center == null || LocationUtils.distanceBetween(center, station.getCoordinates(), true) <= range + 0.001)
                .collect(Collectors.toList());
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        stations.stream().forEach(station -> {
            chainsToStations.putIfAbsent(station.getChain(), new ArrayList<>());
            if (!chainsToStations.get(station.getChain()).contains(station)) {
                chainsToStations.get(station.getChain()).add(station);
            }
        });
        return true;
    }

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    public void close() {
        chainsToStations.clear();
    }
}
