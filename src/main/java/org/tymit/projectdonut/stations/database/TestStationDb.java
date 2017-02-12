package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
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
public class TestStationDb implements StationDbInstance.AreaDb, StationDbInstance.ScheduleDb {

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
    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {
        List<TransStation> stationsToCheck = (chain != null) ? chainsToStations.get(chain) : null;
        if (stationsToCheck == null) {
            stationsToCheck = chainsToStations.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }
        return stationsToCheck.parallelStream()
                .filter(station -> center == null || LocationUtils.distanceBetween(center, station.getCoordinates(), true) <= range + 0.001)
                .collect(Collectors.toList());
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

    @Override
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
