package org.tymit.projectdonut.stations.test;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
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
public class TestStationDb implements StationDbInstance.ComboDb {

    private static final TransChain NULL_CHAIN = new TransChain("NULLCHAINNAME");
    private static final Map<TransChain, List<TransStation>> chainsToStations = new ConcurrentHashMap<>();

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
    public boolean putStations(List<TransStation> stations) {
        stations.forEach(station -> {
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

    public List<TransStation> queryStations(LocationPoint center, Distance range, TransChain chain) {
        List<TransStation> stationsToCheck = (chain != null) ? chainsToStations.get(chain) : chainsToStations
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return stationsToCheck.parallelStream()
                .filter(station -> center == null || LocationUtils.distanceBetween(center, station)
                        .inMeters() <= range.inMeters() + 1)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransStation> queryStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {

        if (center == null || range == null || range.inMeters() < 0) return queryStations(startTime, maxDelta, chain);

        List<TransStation> stationsToCheck = (chain != null) ? chainsToStations.get(chain) : chainsToStations
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return stationsToCheck.parallelStream()
                .filter(station -> LocationUtils.distanceBetween(center, station).inMeters() <= range.inMeters() + 0.1)
                .filter(st -> startTime == null || maxDelta == null || startTime
                        .timeUntil(st.getNextArrival(startTime))
                        .getDeltaLong() <= maxDelta.getDeltaLong())
                .collect(Collectors.toList());
    }

    public List<TransStation> queryStations(TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        List<TransStation> stationsToCheck = (chain != null) ? chainsToStations.get(chain) : chainsToStations
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return stationsToCheck.parallelStream()
                .filter(st -> startTime.timeUntil(st.getNextArrival(startTime))
                        .getDeltaLong() <= maxDelta.getDeltaLong())
                .collect(Collectors.toList());
    }
}
