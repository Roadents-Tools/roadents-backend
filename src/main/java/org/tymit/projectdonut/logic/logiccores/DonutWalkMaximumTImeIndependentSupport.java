package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DonutWalkMaximumTImeIndependentSupport {

    public static Function<Map<? extends LocationPoint, TimeDelta>, Map<? extends LocationPoint, TimeDelta>> nextLayerFilter = map -> {
        List<? extends LocationPoint> badKeys = map.entrySet().stream()
                .filter(entry -> entry.getValue().getDeltaLong() <= 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        badKeys.forEach(map::remove);
        return map;
    };
    private static String TAG = "DonutWalkIndep";

    /**
     * Builds a list of possible routes to stations given initial requirements. Assuming we start at initialPoint
     * at time startTime, each of the returned routes will be to a station where we have walked no more than maxDelta time.
     *
     * @param initialPoint the location we are starting from
     * @param maxDelta     the maximum time from start we are allowed to walk in our journey
     * @return the possible routes
     */
    public static Map<LocationPoint, TimeDelta> buildStationRouteList(StartPoint initialPoint, TimeDelta maxDelta) {
        Map<LocationPoint, TimeDelta> rval = new ConcurrentHashMap<>();
        Map<LocationPoint, TimeDelta> currentLayer = new HashMap<>();
        currentLayer.put(initialPoint, maxDelta);

        while (!currentLayer.isEmpty()) {
            Map<LocationPoint, TimeDelta> nextLayer = currentLayer.entrySet().stream()
                    .map(entry -> getAllPossibleStations(entry.getKey(), entry.getValue()))
                    .map(nextLayerFilter)
                    .peek(rval::putAll)
                    .collect(HashMap::new, HashMap::putAll, HashMap::putAll);


            LoggingUtils.logMessage(TAG, "Next recursive layer size: %d", nextLayer.size());
            currentLayer = nextLayer;
        }
        LoggingUtils.logMessage(TAG, "Exiting from main loop.");
        return rval;
    }

    /**
     * Get all possible stations directly travelable to from a given point. It does not calculate in-between stops.
     *
     * @param center   the point to start from
     * @param maxDelta the maximum time to walk
     * @return a set of nodes representing traveling from center directly to the possible stations
     */
    public static Map<TransStation, TimeDelta> getAllPossibleStations(LocationPoint center, TimeDelta maxDelta) {
        if (!(center instanceof TransStation)) return getWalkableStations(center, maxDelta);
        TransStation station = (TransStation) center;

        Map<TransStation, TimeDelta> walkable = getWalkableStations(station, maxDelta);

        Map<TransStation, TimeDelta> arrivable = DonutLogicSupport.getAllChainsForStop(station).stream()
                .map(station1 -> getArrivableStations(station1, maxDelta))
                .collect(HashMap::new, HashMap::putAll, HashMap::putAll);

        Map<TransStation, TimeDelta> rval = new HashMap<>();
        rval.putAll(walkable);
        for (TransStation key : arrivable.keySet()) {
            rval.merge(key, arrivable.get(key), (timeDelta, timeDelta2) -> (timeDelta == null || timeDelta.getDeltaLong() < timeDelta2
                    .getDeltaLong()) ? timeDelta2 : timeDelta);
        }
        return rval;
    }

    /**
     * Get all stations directly travelable to without walking.
     *
     * @param station the station to start at
     * @return nodes representing directly travelling from station to all possible stations
     */
    public static Map<TransStation, TimeDelta> getArrivableStations(TransStation station, TimeDelta maxDelta) {
        if (station.getChain() == null) return Collections.emptyMap();

        LoggingUtils.logMessage("DonutWalkRange::getArrivableStations", "Starting method with arguments: %s,", station.toString());

        Map<TransStation, TimeDelta> rval = StationRetriever.getStations(null, -1, null, null, station.getChain(), null)
                .stream()
                .filter(fromChain -> !Arrays.equals(fromChain.getCoordinates(), station.getCoordinates()))
                .map(DonutLogicSupport::getStationWithSchedule)
                .collect(HashMap::new, (map, stationKey) -> map.put(stationKey, maxDelta), HashMap::putAll);
        LoggingUtils.logMessage(TAG, "Returing %d stations.", rval.size());
        return rval;
    }

    /**
     * Get all stations within a certain walking time, independent of start time and arrival time.
     *
     * @param begin    the location to start at
     * @param maxDelta the maximum walking time
     * @return nodes representing walking to all possible stations
     */
    public static Map<TransStation, TimeDelta> getWalkableStations(LocationPoint begin, TimeDelta maxDelta) {

        Map<TransStation, TimeDelta> rval = new HashMap<>();

        List<TransStation> stations = StationRetriever.getStations(
                begin.getCoordinates(),
                LocationUtils.timeToWalkDistance(maxDelta.getDeltaLong(), true),
                null,
                null,
                null,
                null
        );

        stations.forEach(station -> {
            TimeDelta timeLeft = maxDelta.minus(new TimeDelta(LocationUtils.timeBetween(begin.getCoordinates(), station.getCoordinates())));
            if (timeLeft.getDeltaLong() > 0) rval.put(station, timeLeft);
        });

        return rval;
    }
}
