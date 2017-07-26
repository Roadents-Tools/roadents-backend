package org.tymit.projectdonut.logic.donut;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.model.routing.TravelRouteNode;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Static utilities for the DonutLogicCore.
 */
public final class DonutLogicSupport {

    private static final int WALKING_LIMIT = 200;
    private static final int CHAINS_PER_STOP_LIMIT = 100;
    private static final String TAG = "DonutLogicSupport";

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

            LoggingUtils.logMessage(TAG, "Next recursive layer size: %d", nextLayer.size());
            currentLayer = nextLayer;
        }
        return new HashSet<>(rval.values());
    }

    public static Function<TravelRoute, Stream<TravelRoute>> buildNextLayerFunction(TimePoint startTime, TimeDelta maxDelta) {
        return route -> {
            TimePoint effectiveTime = startTime.plus(route.getTotalTime());
            TimeDelta effectiveDelta = maxDelta.minus(route.getTotalTime());

            //Version 2 iterator
            //Steps:
            //1. Get all walkable around current end.
            //2. Get all arrivable around walkable.
            //3. Pair the walkable with arrivable.
            //   If a walkable has no arrivable, then it isn't paired and discarded.
            //4. For each pair, construct the new routes.
            //   This means that each new route actually has 2 new nodes, not 1.
            return getWalkableStations(route.getCurrentEnd(), effectiveTime, effectiveDelta).stream()
                    .flatMap(base -> getArrivalNodesForBase(base, effectiveTime, effectiveDelta)
                            .map(aNode -> new TravelRouteNode[] { base, aNode })
                    )
                    .map(nodePair -> route.clone().addNode(nodePair[0]).addNode(nodePair[1]));
        };
    }

    public static Stream<TravelRouteNode> getArrivalNodesForBase(TravelRouteNode base, TimePoint startTime, TimeDelta effectiveDelta) {
        TimeDelta truDelta = effectiveDelta.minus(base.getTotalTimeToArrive());
        TimePoint truStart = startTime.plus(base.getTotalTimeToArrive());
        TransStation pt = (TransStation) base.getPt();
        Stream<TravelRouteNode> rval = getAllChainsForStop(pt, truStart, truDelta).stream()
                .map(cstat -> getArrivableStations(cstat, truStart, truDelta))
                .flatMap(Collection::stream);
        return rval;
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


        List<TransStation> stats = StationRetriever.getStations(
                station.getCoordinates(),
                LocationUtils.timeToWalkDistance(maxDelta.getDeltaLong(), true),
                trueStart,
                maxDelta,
                station.getChain(),
                null
        );

        Set<TravelRouteNode> rval = stats
                .stream()
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

        if (station.getChain() == null || (station.getSchedule() != null && !station.getSchedule().isEmpty())) {
            return station;
        }

        List<TransStation> trueStation = StationRetriever.getStations(station.getCoordinates(), 0, null, null, station.getChain(), null);
        if (trueStation.size() != 1 || trueStation.get(0).getSchedule() == null || trueStation.get(0).getSchedule().isEmpty()) {
            LoggingUtils.logError(TAG, "Error in requery.\nData:\nList size: %d\nTrueStation: %s",
                    trueStation.size(),
                    (!trueStation.isEmpty()) ? trueStation.get(0).toString() : "Null");
            return station;
        }
        return trueStation.get(0);
    }

    public static Set<TransStation> getAllChainsForStop(TransStation orig, TimePoint startTime, TimeDelta maxDelta) {
        Set<TransStation> rval = StationRetriever.getChainsForStation(orig.getCoordinates(), null)
                .stream()
                .filter(stat -> startTime.timeUntil(stat.getNextArrival(startTime))
                        .getDeltaLong() <= maxDelta.getDeltaLong())
                .limit(CHAINS_PER_STOP_LIMIT)
                .distinct()
                .collect(Collectors.toSet());
        rval.add(orig);
        return rval;
    }

    /**
     * Get all stations within a certain walking time.
     *
     * @param begin    the location to start at
     * @param maxDelta the maximum walking time
     * @return nodes representing walking to all possible stations
     */
    public static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimePoint startTime, TimeDelta maxDelta) {
        return StationRetriever.getStrippedStations(
                begin.getCoordinates(),
                LocationUtils.timeToWalkDistance(maxDelta.getDeltaLong(), true),
                WALKING_LIMIT,
                null
        )
                .stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(begin.getCoordinates(), point.getCoordinates()))
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    /**
     * Get all possible TransChains for a given TransStation.
     * @param orig the original TransStation to use
     * @return TransStations for all chains at the location of orig
     */
    public static Set<TransStation> getAllChainsForStop(TransStation orig) {
        Set<TransStation> rval = StationRetriever.getChainsForStation(orig.getCoordinates(), null)
                .stream()
                .distinct()
                .collect(Collectors.toSet());
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
            return currentInMap.getTotalTime().getDeltaLong() > route.getTotalTime().getDeltaLong();
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
                        .setWalkTime(LocationUtils.timeBetween(center.getCoordinates(), point.getCoordinates()))
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }
}
