package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.*;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCore implements LogicCore {

    private static String TAG = "DONUT";
    private static String START_TIME_TAG = "starttime";
    private static String LAT_TAG = "latitude";
    private static String LONG_TAG = "longitude";
    private static String TYPE_TAG = "type";
    private static String TIME_DELTA_TAG = "timedelta";

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        //Get the args
        long startUnixTime = (long) args.get(START_TIME_TAG);
        TimeModel startTime = TimeModel.fromUnixTime(startUnixTime);
        double startLat = (double) args.get(LAT_TAG);
        double startLong = (double) args.get(LONG_TAG);
        long maxUnixTimeDelta = (long) args.get(TIME_DELTA_TAG);
        TimeModel maxTimeDelta = TimeModel.fromUnixTimeDelta(maxUnixTimeDelta);
        LocationType type = new LocationType((String) args.get(TYPE_TAG), (String) args.get(TYPE_TAG));

        TravelRoute startRoute = new TravelRoute(new StartPoint(new double[]{startLat, startLong}), startTime);
        startRoute.putCost(TIME_DELTA_TAG, 0l);
        Set<TravelRoute> startRouteSet = new HashSet<>();
        startRouteSet.add(startRoute);
        Set<TravelRoute> endRoutes = performRecurse(startRoute, maxTimeDelta, startRouteSet).stream()
                .flatMap(route -> {
                    long routeDelta = (long) route.getCosts().get(TIME_DELTA_TAG);
                    TimeModel trueDelta = TimeModel.fromUnixTimeDelta(maxUnixTimeDelta - routeDelta);
                    return getDestinationRoutes(route, trueDelta, type).stream();
                })
                .collect(Collectors.toSet());

        ConcurrentMap<DestinationLocation, TravelRoute> destToShortest = new ConcurrentHashMap<>();
        endRoutes.stream()
                .forEach(route -> {
                    DestinationLocation dest = route.getDestination();
                    if (destToShortest.putIfAbsent(dest, route) == null) return;
                    TravelRoute oldRoute = destToShortest.get(dest);
                    long oldLong = (long) oldRoute.getCosts().get(TIME_DELTA_TAG);
                    long thisLong = (long) route.getCosts().get(TIME_DELTA_TAG);
                    if (oldLong > thisLong && !destToShortest.replace(dest, oldRoute, route)) {
                        LoggingUtils.logError(getTag(), "Donut route filtering concurrency issue.");
                    }
                });


        //Build the output
        Map<String, List<Object>> output = new HashMap<>();
        if (LoggingUtils.hasErrors()) {
            List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
            output.put("ERRORS", errs);
        }
        output.put("DESTS", new ArrayList<>());
        output.put("ROUTES", new ArrayList<>());
        for (TravelRoute route : destToShortest.values()) {
            output.get("DESTS").add(route.getDestination());
            output.get("ROUTES").add(route);
        }
        return output;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private Set<TravelRoute> performRecurse(TravelRoute initial, TimeModel maxDelta, Set<TravelRoute> existingRoutes) {


        final long initialDeltaUnix = (long) initial.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        Set<TravelRoute> possibleStationRoutes = getAllPossibleStations(initial, maxDelta);
        if (possibleStationRoutes == null || possibleStationRoutes.size() == 0) return existingRoutes;

        Set<TravelRoute> routes = new HashSet<>(existingRoutes);
        routes.addAll(possibleStationRoutes);

        Set<TravelRoute> recursed = possibleStationRoutes.parallelStream()
                .filter(route -> !existingRoutes.contains(route))
                .flatMap(route -> {
                    long maxDeltaUnix = maxDelta.getUnixTimeDelta();
                    long newDeltaUnix = maxDeltaUnix - (long) route.getCosts().get(TIME_DELTA_TAG) + initialDeltaUnix;
                    TimeModel newDelta = TimeModel.fromUnixTimeDelta(newDeltaUnix);
                    return performRecurse(route, newDelta, routes).parallelStream();
                })
                .collect(Collectors.toSet());
        routes.addAll(recursed);
        return routes;
    }

    private Set<TravelRoute> getAllPossibleStations(TravelRoute initial, TimeModel maxDelta) {
        long initialDelta = (long) initial.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        Set<TravelRoute> allRoutes = new HashSet<>();
        allRoutes.add(initial);
        getWalkableStationRoutes(initial, maxDelta).parallelStream()
                .flatMap(route -> {
                    allRoutes.add(route);
                    long routeDelta = (long) route.getCosts().get(TIME_DELTA_TAG);
                    TimeModel trueStart = TimeModel.fromUnixTime(route.getStartTime().getUnixTime() + routeDelta);
                    TimeModel trueDelta = TimeModel.fromUnixTimeDelta(maxDelta.getUnixTimeDelta() - routeDelta + initialDelta);
                    return getArrivableChainRoutes(route, (TransStation) route.getCurrentEnd(), trueStart, trueDelta).stream();
                })
                .forEach(route -> allRoutes.add(route));
        return allRoutes;
    }

    private Set<TravelRoute> getWalkableStationRoutes(TravelRoute initial, TimeModel maxDelta) {
        Map<TransStation, Long> walkableToTimeDiff = getWalkableStations(initial.getCurrentEnd(), maxDelta);
        Set<TravelRoute> rval = walkableToTimeDiff.keySet().parallelStream()
                .filter(inChain -> !initial.isInRoute(inChain))
                .filter(inChain -> maxDelta.compareTo(TimeModel.fromUnixTimeDelta(walkableToTimeDiff.get(inChain))) > 0)
                .map(station -> {
                    TravelRoute newRoute = initial.clone();
                    addTimeToRoute(newRoute, walkableToTimeDiff.get(station));
                    newRoute.addStation(station);
                    return newRoute;
                })
                .collect(Collectors.toSet());
        return rval;
    }

    private Map<TransStation, Long> getWalkableStations(LocationPoint begin, TimeModel maxDelta) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<TransStation> stationsInRange = StationRetriever.getStations(begin.getCoordinates(), range, null, null);
        return getStationWalkTime(begin, stationsInRange);
    }

    private Map<TransStation, Long> getStationWalkTime(LocationPoint begin, Collection<TransStation> stations) {
        Map<TransStation, Long> rval = new ConcurrentHashMap<>();
        stations.parallelStream().forEach(station -> {
            double dist = LocationUtils.distanceBetween(begin.getCoordinates(), station.getCoordinates(), true);
            long time = LocationUtils.distanceToWalkTime(dist, true);
            rval.put(station, time);
        });
        return rval;
    }

    private Set<TravelRoute> getArrivableChainRoutes(TravelRoute initial, TransStation station, TimeModel startTime, TimeModel maxDelta) {
        Map<TransStation, Long> arrivableToTimes = getArrivableChainTimes(station, startTime, maxDelta);
        return arrivableToTimes.keySet().parallelStream()
                .filter(inChain -> !initial.isInRoute(inChain))
                .map(inChain -> {
                    TravelRoute newRoute = initial.clone();
                    addTimeToRoute(newRoute, arrivableToTimes.get(inChain));
                    newRoute.addStation(inChain);
                    return newRoute;
                })
                .collect(Collectors.toSet());
    }

    private Map<TransStation, Long> getArrivableChainTimes(TransStation station, TimeModel startTime, TimeModel maxDelta) {
        Map<TransStation, Long> rval = new ConcurrentHashMap<>();
        if (station.getChain() == null) return rval;
        TimeModel trueStart = station.getNextArrival(startTime);

        List<TransStation> inChain = StationRetriever.getStations(null, 0, station.getChain(), null);
        inChain.parallelStream().forEach(fromChain -> {
            TimeModel arriveTime = fromChain.getNextArrival(trueStart);
            long timeFromStart = arriveTime.getUnixTime() - startTime.getUnixTime();
            if (timeFromStart > maxDelta.getUnixTimeDelta()) return;
            rval.put(fromChain, timeFromStart);
        });
        return rval;
    }

    private Set<TravelRoute> getDestinationRoutes(TravelRoute initial, TimeModel maxDelta, LocationType type) {
        Map<DestinationLocation, Long> destsToTimes = getWalkableDestinations(initial.getCurrentEnd(), maxDelta, type);
        return destsToTimes.keySet().parallelStream()
                .map(dest -> {
                    TravelRoute newRoute = initial.clone();
                    addTimeToRoute(newRoute, destsToTimes.get(dest));
                    newRoute.setDestination(dest);
                    return newRoute;
                })
                .collect(Collectors.toSet());
    }

    private Map<DestinationLocation, Long> getWalkableDestinations(LocationPoint center, TimeModel maxDelta, LocationType type) {
        Map<DestinationLocation, Long> rval = new ConcurrentHashMap<>();
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<DestinationLocation> possibleDests = LocationRetriever.getLocations(center.getCoordinates(), range, type, null);
        possibleDests.parallelStream().forEach(destinationLocation -> {
            double dist = LocationUtils.distanceBetween(center.getCoordinates(), destinationLocation.getCoordinates(), true);
            long time = LocationUtils.distanceToWalkTime(dist, true);
            rval.put(destinationLocation, time);
        });
        return rval;
    }

    private void addTimeToRoute(TravelRoute route, long timeDeltaLong) {
        long oldTime = (long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        long newTime = oldTime + timeDeltaLong;
        route.putCost(TIME_DELTA_TAG, newTime);
    }
}
