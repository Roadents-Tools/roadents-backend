package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.*;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/27/16.
 */
public class DonutLogicSupport {

    public static String TIME_DELTA_TAG = "timedelta";

    public static void buildStationRouteList(TravelRoute initial, TimeModel startTime, TimeModel maxDelta, Map<LocationPoint, TravelRoute> collector) {

        long initialDelta = (long) initial.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        if (initialDelta >= maxDelta.getUnixTimeDelta()) return;

        TimeModel trueStart = startTime.addUnixTime(initialDelta);
        TimeModel deltaLeft = maxDelta.addUnixTime(-1 * initialDelta);

        Map<TransStation, Long> allPossibleStations = getAllPossibleStations(initial.getCurrentEnd(), trueStart, deltaLeft);

        List<TravelRoute> newRoutesList = addStationsToRoute(initial, allPossibleStations);
        Set<TravelRoute> newRoutes = new HashSet<>(newRoutesList);
        newRoutes.remove(initial);

        newRoutes.stream()

                //Filter routes that have gone over time
                .filter(route -> ((long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l)) < maxDelta.getUnixTimeDelta())

                //We should only have 1 path to get to any location -- the shortest.
                //We use a map to guarantee this and then filter all those with greater times than
                //the one already in the map.
                .filter(route -> {
                    LocationPoint currentEnd = route.getCurrentEnd();
                    if (collector.keySet().contains(currentEnd)) {
                        long oldTime = (long) collector.get(currentEnd).getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
                        long newTime = (long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
                        return oldTime < newTime;
                    }
                    return true;
                })

                //Finally, collect and recurse.
                .forEach(route -> {
                    collector.put(route.getCurrentEnd(), route);
                    buildStationRouteList(route, startTime, maxDelta, collector);
                });
    }

    public static Map<DestinationLocation, Long> getWalkableDestinations(LocationPoint center, TimeModel maxDelta, LocationType type) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<DestinationLocation> possibleDests = LocationRetriever.getLocations(center.getCoordinates(), range, type, null);
        return getWalkTimes(center, possibleDests);
    }

    public static <T extends LocationPoint> Map<T, Long> getWalkTimes(LocationPoint begin, Collection<T> points) {
        Map<T, Long> rval = new ConcurrentHashMap<>();
        points.stream().forEach(point -> {
            double dist = LocationUtils.distanceBetween(begin.getCoordinates(), point.getCoordinates(), true);
            long time = LocationUtils.distanceToWalkTime(dist, true);
            rval.put(point, time);
        });
        return rval;
    }


    public static Map<TransStation, Long> getAllPossibleStations(LocationPoint center, TimeModel startTime, TimeModel maxDelta) {
        if (!(center instanceof TransStation)) return getWalkableStations(center, maxDelta);
        TransStation station = (TransStation) center;

        Map<TransStation, Long> walkable = getWalkableStations(station, maxDelta);
        Map<TransStation, Long> arrivable = getArrivableStations(station, startTime, maxDelta);

        Map<TransStation, Long> rval = new ConcurrentHashMap<>(walkable);
        arrivable.keySet().stream().forEach(keyStation -> {
            if (walkable.containsKey(keyStation)) {
                long walkableDelta = walkable.get(keyStation);
                long arrivableDelta = arrivable.get(keyStation);
                long min = (walkableDelta > arrivableDelta) ? arrivableDelta : walkableDelta;
                rval.put(keyStation, min);
            } else {
                rval.put(keyStation, arrivable.get(keyStation));
            }
        });
        return rval;
    }

    public static Map<TransStation, Long> getWalkableStations(LocationPoint begin, TimeModel maxDelta) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<TransStation> stationsInRange = StationRetriever.getStations(begin.getCoordinates(), range, null, null);
        return getWalkTimes(begin, stationsInRange);
    }

    public static List<TravelRoute> addStationsToRoute(TravelRoute initial, Map<TransStation, Long> stationsToDeltas) {
        return stationsToDeltas.keySet().stream()
                .map(station -> {
                    TravelRoute newRoute = initial.clone();
                    newRoute.addStation(station);
                    addTimeToRoute(newRoute, stationsToDeltas.get(station));
                    return newRoute;
                }).collect(Collectors.toList());
    }

    public static Map<TransStation, Long> getArrivableStations(TransStation station, TimeModel startTime, TimeModel maxDelta) {
        Map<TransStation, Long> rval = new ConcurrentHashMap<>();
        if (station.getChain() == null) return rval;
        TimeModel trueStart = station.getNextArrival(startTime);

        List<TransStation> inChain = StationRetriever.getStations(null, 0, station.getChain(), null);
        inChain.stream().forEach(fromChain -> {
            TimeModel arriveTime = fromChain.getNextArrival(trueStart);
            long timeFromStart = arriveTime.getUnixTime() - startTime.getUnixTime();
            if (timeFromStart > maxDelta.getUnixTimeDelta()) return;
            rval.put(fromChain, timeFromStart);
        });
        return rval;
    }

    public static List<TravelRoute> addDestinationsToRoute(TravelRoute initial, Map<DestinationLocation, Long> destsToDeltas) {
        return destsToDeltas.keySet().stream()
                .map(dest -> {
                    TravelRoute newRoute = initial.clone();
                    newRoute.setDestination(dest);
                    addTimeToRoute(newRoute, destsToDeltas.get(dest));
                    return newRoute;
                }).collect(Collectors.toList());
    }

    public static void addTimeToRoute(TravelRoute route, long timeDeltaLong) {
        long oldTime = (long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        long newTime = oldTime + timeDeltaLong;
        route.putCost(TIME_DELTA_TAG, newTime);
    }
}
