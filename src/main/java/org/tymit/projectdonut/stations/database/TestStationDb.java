package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/10/16.
 */
public class TestStationDb implements StationDbInstance {

    private Map<TransChain, List<TransStation>> chainsToStations = new ConcurrentHashMap<>();

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
}
