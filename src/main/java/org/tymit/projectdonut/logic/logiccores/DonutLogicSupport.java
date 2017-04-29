package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Static utilities for the DonutLogicCore.
 */
public final class DonutLogicSupport {

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
                if (current == null || current.getTotalTime()
                        .getDeltaLong() > route.getTotalTime().getDeltaLong())
                    curmap.put(dest, route);
            };
        }

        @Override
        public BinaryOperator<Map<DestinationLocation, TravelRoute>> combiner() {
            return (curmap, curmap2) -> {
                for (DestinationLocation key : curmap2.keySet()) {
                    TravelRoute current = curmap.get(key);
                    TravelRoute current2 = curmap2.get(key);
                    if (current == null || current.getTotalTime()
                            .getDeltaLong() > current2.getTotalTime()
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
            return Sets.newHashSet(Characteristics.IDENTITY_FINISH);
        }
    };

    /**
     * Utility classes cannot be initialized.
     */
    private DonutLogicSupport() {
    }

    /**
     * Builds a list of possible routes to stations given initial requirements. Assuming we start at initialPoint
     * at time startTime, each of the returned routes will be to a station no more than maxDelta time away.
     *
     * @param initialPoint the location we are starting from
     * @param startTime    the time we begin looking for stations
     * @param maxDelta     the maximum time from start we are allowed to travel to get to the stations
     * @return the possible routes
     */
    public static Set<TravelRoute> buildStationRouteList(StartPoint initialPoint, TimePoint startTime, TimeDelta maxDelta) {
        Map<String, TravelRoute> rval = new ConcurrentHashMap<>();
        TravelRoute startRoute = new TravelRoute(initialPoint, startTime);
        rval.put(getLocationTag(initialPoint), startRoute);
        List<TravelRoute> currentLayer = new ArrayList<>(Sets.newHashSet(startRoute));

        Function<TravelRoute, Stream<TravelRoute>> unfilteredLayerBuilder = buildNextLayerFunction(startTime, maxDelta);
        while (!currentLayer.isEmpty()) {
            List<TravelRoute> nextLayer = currentLayer.stream()
                    .flatMap(unfilteredLayerBuilder)
                    .filter(nextLayerFilter(maxDelta, rval))
                    .peek(newRoute -> rval.put(getLocationTag(newRoute.getCurrentEnd()), newRoute))
                    .collect(Collectors.toList());

            LoggingUtils.logMessage("DONUT", "Next recursive layer size: %d", nextLayer
                    .size());
            currentLayer = nextLayer;
        }
        return new HashSet<>(rval.values());
    }

    public static Function<TravelRoute, Stream<TravelRoute>> buildNextLayerFunction(TimePoint startTime, TimeDelta maxDelta) {
        return route -> getAllPossibleStations(route.getCurrentEnd(), startTime.plus(route
                .getTotalTime()), maxDelta.minus(route.getTotalTime()))
                .stream()
                .map(node -> route.clone().addNode(node));
    }

    /**
     * Get all possible stations directly travelable to from a given point. It does not calculate in-between stops.
     * @param center the point to start from
     * @param startTime the time to start at
     * @param maxDelta the maximum time to travel
     * @return a set of nodes representing traveling from center directly to the possible stations
     */
    public static Set<TravelRouteNode> getAllPossibleStations(LocationPoint center, TimePoint startTime, TimeDelta maxDelta) {
        if (!(center instanceof TransStation)) return getWalkableStations(center, maxDelta);
        TransStation station = (TransStation) center;

        Map<TransStation, TravelRouteNode> walkable = getWalkableStations(station, maxDelta).stream()
                .collect(ConcurrentHashMap::new, (map, node) -> map.put((TransStation) node.getPt(), node), ConcurrentHashMap::putAll);

        Map<TransStation, TravelRouteNode> arrivable = getAllChainsForStop(station).stream()
                .flatMap(station1 -> getArrivableStations(station1, startTime, maxDelta).stream())
                .collect(ConcurrentHashMap::new, (map, node) -> map.put((TransStation) node.getPt(), node), ConcurrentHashMap::putAll);

        Set<TransStation> allPossibleStations = new HashSet<>();
        allPossibleStations.addAll(walkable.keySet());
        allPossibleStations.addAll(arrivable.keySet());

        return allPossibleStations.stream()
                .distinct()
                .map(keyStation -> {
                    if (walkable.containsKey(keyStation) && !arrivable.containsKey(keyStation)) {
                        return walkable.get(keyStation);
                    }
                    if (!walkable.containsKey(keyStation) && arrivable.containsKey(keyStation)) {
                        return arrivable.get(keyStation);
                    }
                    if (walkable.get(keyStation)
                            .getTotalTimeToArrive()
                            .getDeltaLong() < arrivable.get(keyStation)
                            .getTotalTimeToArrive()
                            .getDeltaLong()) {
                        return walkable.get(keyStation);
                    }
                    return arrivable.get(keyStation);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Get all stations within a certain walking time.
     * @param begin the location to start at
     * @param maxDelta the maximum walking time
     * @return nodes representing walking to all possible stations
     */
    public static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimeDelta maxDelta) {
        return StationRetriever.getStations(begin.getCoordinates(), LocationUtils
                .timeToWalkDistance(maxDelta.getDeltaLong(), true), null, null)
                .stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(begin.getCoordinates(), point
                                .getCoordinates()))
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    /**
     * Get all stations directly travelable to within a given time.
     * @param station the station to start at
     * @param startTime the time to start at
     * @param maxDelta the maximum time to travel
     * @return nodes representing directly travelling from station to all possible stations
     */
    public static Set<TravelRouteNode> getArrivableStations(TransStation station, TimePoint startTime, TimeDelta maxDelta) {
        if (station.getChain() == null) return Collections.emptySet();

        TimePoint trueStart = getStationWithSchedule(station).getNextArrival(startTime);

        TimeDelta waitTime = startTime.timeUntil(trueStart);

        if (waitTime.getDeltaLong() >= maxDelta.getDeltaLong()) {
            return Collections.emptySet();
        }

        Set<TravelRouteNode> rval = StationRetriever.getStations(trueStart, maxDelta, station
                .getChain(), null)./*parallelS*/stream()
                .filter(fromChain -> !Arrays.equals(fromChain.getCoordinates(), station.getCoordinates()))
                .map(DonutLogicSupport::getStationWithSchedule)
                .filter(fromChain -> startTime.timeUntil(fromChain.getNextArrival(trueStart))
                        .getDeltaLong() <= maxDelta.getDeltaLong())
                .map(fromChain -> new TravelRouteNode.Builder()
                        .setWaitTime(waitTime.getDeltaLong())
                        .setPoint(fromChain)
                        .setTravelTime(fromChain.getNextArrival(trueStart).getUnixTime() - trueStart.getUnixTime())
                        .build()
                )
                .collect(Collectors.toSet());
        return rval;
    }

    /**
     * Get the schedule for a given station.
     * @param station the possibly schedule free station to use
     * @return the station complete with schedule
     */
    public static TransStation getStationWithSchedule(TransStation station) {
        if (!(station.getSchedule() == null || station.getSchedule().isEmpty())) return station;

        List<TransStation> trueStation = StationRetriever.getStations(station.getCoordinates(), 0.001, station.getChain(), null);
        if (trueStation.size() != 1 || trueStation.get(0).getSchedule() == null || trueStation.get(0).getSchedule().isEmpty()) {
            LoggingUtils.logError("DONUT", "Error in requery.\nData:\nList size: %d\nTrueStation: %s",
                    trueStation.size(),
                    (!trueStation.isEmpty()) ? trueStation.get(0).toString() : "Null");
            return station;
        }
        return trueStation.get(0);
    }

    /**
     * Get all possible TransChains for a given TransStation.
     * @param orig the original TransStation to use
     * @return TransStations for all chains at the location of orig
     */
    public static Set<TransStation> getAllChainsForStop(TransStation orig) {
        List<TransStation> all = StationRetriever.getStations(orig.getCoordinates(), 0, null, null);
        Set<TransStation> rval = new HashSet<>(all);
        rval.add(orig);
        return rval;
    }

    public static Predicate<TravelRoute> nextLayerFilter(TimeDelta maxDelta, Map<String, TravelRoute> currentRoutes) {
        return route -> {
            if (route.getTotalTime().getDeltaLong() >= maxDelta.getDeltaLong())
                return false;
            if (isMiddleMan(route))
                return false; //TODO: Not bandaid the middleman issue
            TravelRoute currentInMap = currentRoutes.get(getLocationTag(route.getCurrentEnd()));
            if (currentInMap == null) return true;
            return currentInMap.getTotalTime()
                    .getDeltaLong() > route.getTotalTime().getDeltaLong();
        };
    }

    /**
     * Checks to see if a travelroute suffered from the middleman issue.
     * This is defined as a route telling the user to walk to 2 different places in a row;
     * a situation that, since our algorithm walks as the crow flies, should never happen
     * in an optimized route.
     *
     * @param route the route to check
     * @return whether or not this is a middleman error
     */
    public static boolean isMiddleMan(TravelRoute route) {
        int rtSize = route.getRoute().size();
        return IntStream.range(1, rtSize)
                .boxed()
                .parallel()
                .anyMatch(i -> route.getRoute()
                        .get(i - 1)
                        .arrivesByFoot() && route.getRoute()
                        .get(i)
                        .arrivesByFoot());
    }

    /**
     * Get a tag to represent a single location, for filtering purposes.
     * @param pt the location
     * @return the location's tag
     */
    public static String getLocationTag(LocationPoint pt) {
        return pt.getCoordinates()[0] + ", " + pt.getCoordinates()[1];
    }

    /**
     * Get all the destinations walkable in a given area.
     *
     * @param center   the center of the area
     * @param maxDelta the maximum time we will walk
     * @param type     the type of destination to find
     * @return the found destinations
     */
    public static Set<TravelRouteNode> getWalkableDestinations(LocationPoint center, TimeDelta maxDelta, LocationType type) {
        return LocationRetriever.getLocations(center.getCoordinates(), LocationUtils
                .timeToWalkDistance(maxDelta.getDeltaLong(), true), type, null)
                .stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(center.getCoordinates(), point
                                .getCoordinates()))
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }
}
