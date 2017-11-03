package com.reroute.backend.logic.utils;

import com.google.common.collect.Sets;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LogicUtils {

    public static final Collector<? super TravelRoute, ?, Map<DestinationLocation, TravelRoute>> OPTIMAL_ROUTES_FOR_DESTINATIONS = new Collector<TravelRoute, Map<DestinationLocation, TravelRoute>, Map<DestinationLocation, TravelRoute>>() {

        @Override
        public Supplier<Map<DestinationLocation, TravelRoute>> supplier() {
            return ConcurrentHashMap::new;
        }

        @Override
        public BiConsumer<Map<DestinationLocation, TravelRoute>, TravelRoute> accumulator() {
            return (curmap, route) -> {
                DestinationLocation dest = route.getDestination();
                TravelRoute current = curmap.get(dest);
                if (current == null || current.getTime().getDeltaLong() > route.getTime().getDeltaLong())
                    curmap.put(dest, route);
            };
        }

        @Override
        public BinaryOperator<Map<DestinationLocation, TravelRoute>> combiner() {
            return (curmap, curmap2) -> {
                for (DestinationLocation key : curmap2.keySet()) {
                    TravelRoute current = curmap.get(key);
                    TravelRoute current2 = curmap2.get(key);
                    if (current == null || current.getTime().getDeltaLong() > current2.getTime()
                            .getDeltaLong())
                        curmap.put(key, current2);
                }
                return curmap;
            };
        }

        @Override
        public Function<Map<DestinationLocation, TravelRoute>, Map<DestinationLocation, TravelRoute>> finisher() {
            return curmap -> curmap;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.newHashSet(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
        }
    };
    private static final String TAG = "LogicUtils";

    /**
     * Builds a list of possible routes to stations given initial requirements. Assuming we start at initialPoint
     * at time startTime, each of the returned routes will be to a station no more than maxDelta time away.
     *
     * @param initialPoint the location we are starting from
     * @param startTime    the time we begin looking for stations
     * @param maxDelta     the maximum time from start we are allowed to travel to get to the stations
     * @param layerFilters other filters to apply to each layer to make sure it should be iterated through
     * @return the possible routes
     */
    @SafeVarargs
    public static Set<TravelRoute> buildStationRouteList(StartPoint initialPoint, TimePoint startTime, TimeDelta maxDelta, Predicate<TravelRoute>... layerFilters) {
        if (layerFilters == null) {
            return buildStationRouteList(new StationRoutesBuildRequest(initialPoint, startTime, maxDelta));
        } else if (layerFilters.length == 1) {
            return buildStationRouteList(new StationRoutesBuildRequest(initialPoint, startTime, maxDelta).withLayerFilter(layerFilters[0]));
        } else {
            Predicate<TravelRoute> allFilters = Arrays.stream(layerFilters).reduce(a -> true, Predicate::and);
            return buildStationRouteList(new StationRoutesBuildRequest(initialPoint, startTime, maxDelta).withLayerFilter(allFilters));
        }
    }

    public static Set<TravelRoute> buildStationRouteList(StationRoutesBuildRequest request) {
        StartPoint initialPoint = request.getInitialPoint();
        TimePoint startTime = request.getStartTime();
        TimeDelta maxDelta = request.getMaxDelta();
        Predicate<TravelRoute> layerFilters = request.getLayerFilter();
        Predicate<TravelRoute> endFilter = request.getEndFilter();
        int layerLimit = request.getLayerLimit();
        int finalLimit = request.getFinalLimit();

        Map<String, TravelRoute> rval = new ConcurrentHashMap<>();
        TravelRoute startRoute = new TravelRoute(initialPoint, startTime);
        rval.put(getLocationTag(initialPoint), startRoute);
        List<TravelRoute> currentLayer = new ArrayList<>(Sets.newHashSet(startRoute));

        Function<TravelRoute, Stream<TravelRoute>> unfilteredLayerBuilder = buildNextLayerFunction(startTime, maxDelta);
        Predicate<TravelRoute> layerFilter = nextLayerFilter(maxDelta, rval, layerFilters);
        while (!currentLayer.isEmpty()) {
            List<TravelRoute> nextLayer = currentLayer.stream()
                    .flatMap(unfilteredLayerBuilder)
                    .filter(layerFilter)
                    .limit(layerLimit)
                    .peek(newRoute -> rval.put(getLocationTag(newRoute.getCurrentEnd()), newRoute))
                    .collect(Collectors.toList());

            LoggingUtils.logMessage(TAG, "Next recursive layer size: %d", nextLayer.size());
            currentLayer = nextLayer;
        }

        return rval.values().stream()
                .filter(endFilter)
                .limit(finalLimit)
                .collect(Collectors.toSet());
    }

    private static Function<TravelRoute, Stream<TravelRoute>> buildNextLayerFunction(TimePoint startTime, TimeDelta maxDelta) {
        return route -> {
            TimePoint effectiveTime = startTime.plus(route.getTime());
            TimeDelta effectiveDelta = maxDelta.minus(route.getTime());

            //Version 2 iterator
            //Steps:
            //1. Get all walkable around current end.
            //2. Get all arrivable around walkable.
            //3. Pair the walkable with arrivable.
            //   If a walkable has no arrivable, then it isn't paired and discarded.
            //4. For each pair, construct the new routes.
            //   This means that each new route actually has 2 new nodes, not 1.
            return getWalkableStations(route.getCurrentEnd(), effectiveTime, effectiveDelta).parallelStream()
                    .flatMap(base -> getArrivalNodesForBase(base, effectiveTime, effectiveDelta)
                            .map(aNode -> new TravelRouteNode[] { base, aNode })
                    )
                    .map(nodePair -> route.copy().addNode(nodePair[0]).addNode(nodePair[1]));
        };
    }

    private static Stream<TravelRouteNode> getArrivalNodesForBase(TravelRouteNode base, TimePoint startTime, TimeDelta effectiveDelta) {
        TimeDelta truDelta = effectiveDelta.minus(base.getTotalTimeToArrive());
        TimePoint truStart = startTime.plus(base.getTotalTimeToArrive());
        TransStation pt = (TransStation) base.getPt();
        Set<TransStation> allChainsForStop = getAllChainsForStop(pt, truStart, truDelta);
        return allChainsForStop.stream()
                .map(cstat -> getArrivableStations(cstat, truStart, truDelta))
                .flatMap(Collection::stream);
    }

    /**
     * Get all stations within a certain walking time.
     *
     * @param begin    the location to start at
     * @param maxDelta the maximum walking time
     * @return nodes representing walking to all possible stations
     */
    private static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimePoint startTime, TimeDelta maxDelta) {
        Distance range = LocationUtils.timeToWalkDistance(maxDelta);
        return StationRetriever.getStationsInArea(begin, range, null).parallelStream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(begin, point).getDeltaLong())
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    /**
     * Get the schedule for a given station.
     *
     * @param station the possibly schedule free station to use
     * @return the station complete with schedule
     */
    private static TransStation getStationWithSchedule(TransStation station) {

        if (station.getChain() == null || (station.getSchedule() != null && !station.getSchedule().isEmpty())) {
            return station;
        }

        Map<TransChain, List<SchedulePoint>> scheduleMap = StationRetriever.getChainsForStation(station, null);
        List<SchedulePoint> schedule = scheduleMap.get(station.getChain());
        if (schedule == null || schedule.isEmpty()) {
            LoggingUtils.logError(TAG, "Error in requery for station %s.", station.toString());
            return station;
        }
        return station.withSchedule(station.getChain(), schedule);
    }

    /**
     * Get all stations directly travelable to within a given time.
     *
     * @param station   the station to start at
     * @param startTime the time to start at
     * @param maxDelta  the maximum time to travel
     * @return nodes representing directly travelling from station to all possible stations
     */
    public static Set<TravelRouteNode> getArrivableStations(TransStation station, TimePoint startTime, TimeDelta maxDelta) {
        if (station.getChain() == null) return Collections.emptySet();

        TimePoint trueStart = getStationWithSchedule(station).getNextArrival(startTime);
        TimeDelta waitTime = startTime.timeUntil(trueStart);
        TimeDelta trueDelta = maxDelta.minus(startTime.timeUntil(trueStart));


        if (waitTime.getDeltaLong() >= maxDelta.getDeltaLong()) {
            return Collections.emptySet();
        }

        return StationRetriever.getArrivableStations(station.getChain(), trueStart, trueDelta)
                .entrySet().parallelStream()
                .filter(entry -> !Arrays.equals(entry.getKey().getCoordinates(), station.getCoordinates()))
                .filter(entry -> entry.getValue().getDeltaLong() <= trueDelta.getDeltaLong())
                .map(entry -> new TravelRouteNode.Builder()
                        .setWaitTime(waitTime.getDeltaLong())
                        .setPoint(entry.getKey())
                        .setTravelTime(entry.getValue().getDeltaLong())
                        .build())
                .collect(Collectors.toSet());
    }

    private static Set<TransStation> getAllChainsForStop(TransStation orig, TimePoint startTime, TimeDelta maxDelta) {
        Set<TransStation> rval = StationRetriever.getChainsForStation(orig, null).entrySet().parallelStream()
                .map(entry -> orig.withSchedule(entry.getKey(), entry.getValue()))
                .filter(stat -> startTime.timeUntil(stat.getNextArrival(startTime))
                        .getDeltaLong() <= maxDelta.getDeltaLong())
                .distinct()
                .collect(Collectors.toSet());
        if (orig.getChain() != null) rval.add(orig);
        return rval;
    }

    @SafeVarargs
    private static Predicate<TravelRoute> nextLayerFilter(TimeDelta maxDelta, Map<String, TravelRoute> currentRoutes, Predicate<TravelRoute>... others) {
        Predicate<TravelRoute> rval = route -> route.getTime().getDeltaLong() <= maxDelta.getDeltaLong();
        rval = rval.and(
                route -> Optional.ofNullable(currentRoutes.get(getLocationTag(route.getCurrentEnd())))
                        .map(rt -> rt.getTime().getDeltaLong() > route.getTime().getDeltaLong())
                        .orElse(true)
        );
        for (Predicate<TravelRoute> passed : others) {
            rval = rval.and(passed);
        }
        return rval;
    }

    /**
     * Get a tag to represent a single location, for filtering purposes.
     *
     * @param pt the location
     * @return the location's tag
     */
    private static String getLocationTag(LocationPoint pt) {
        return pt.getCoordinates()[0] + ", " + pt.getCoordinates()[1];
    }

    public static Predicate<TravelRoute> isRouteInRange(LocationPoint end, TimeDelta maxDelta) {
        return route -> LocationUtils.distanceBetween(end, route.getCurrentEnd()).inMeters()
                <= LocationUtils.timeToMaxTransit(maxDelta.minus(route.getTime())).inMeters();
    }
}
