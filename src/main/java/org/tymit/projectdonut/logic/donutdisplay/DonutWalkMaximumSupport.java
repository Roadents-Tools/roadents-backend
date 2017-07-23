package org.tymit.projectdonut.logic.donutdisplay;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.logic.donut.DonutLogicSupport;
import org.tymit.projectdonut.logic.utils.StreamUtils;
import org.tymit.projectdonut.model.location.LocationPoint;
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
import java.util.stream.Stream;

public class DonutWalkMaximumSupport {

    public static Map<LocationPoint, TimeDelta> generateDisplayGraph(Collection<? extends TravelRoute> routes, TimeDelta maxDelta) {
        Map<LocationPoint, TimeDelta> rval = new ConcurrentHashMap<>();

        for (TravelRoute route : routes) {
            for (TravelRouteNode node : route.getRoute()) {
                TimeDelta usedTime = route.getWalkTimeAtNode(node);
                TimeDelta usable = maxDelta.minus(usedTime);
                rval.merge(node.getPt(), usable, (timeDelta, timeDelta2) -> timeDelta == null || timeDelta.getDeltaLong() < timeDelta2
                        .getDeltaLong() ? timeDelta2 : timeDelta);
            }
        }

        return rval;
    }


    /**
     * Builds a list of possible routes to stations given initial requirements. Assuming we start at initialPoint
     * at time startTime, each of the returned routes will be to a station where we have walked no more than maxDelta time.
     *
     * @param initialPoint the location we are starting from
     * @param startTime    the time we begin looking for stations
     * @param maxDelta     the maximum time from start we are allowed to walk in our journey
     * @return the possible routes
     */
    public static Set<TravelRoute> buildStationRouteList(StartPoint initialPoint, TimePoint startTime, TimeDelta maxDelta) {
        Map<String, TravelRoute> rval = new ConcurrentHashMap<>();
        TravelRoute startRoute = new TravelRoute(initialPoint, startTime);
        rval.put(DonutLogicSupport.getLocationTag(initialPoint), startRoute);
        List<TravelRoute> currentLayer = new ArrayList<>(Sets.newHashSet(startRoute));

        Function<TravelRoute, Stream<TravelRoute>> unfilteredLayerBuilder = buildNextLayerFunction(startTime, maxDelta);
        while (!currentLayer.isEmpty()) {
            List<TravelRoute> nextLayer = currentLayer.stream()
                    .flatMap(unfilteredLayerBuilder)
                    .filter(nextLayerFilter(maxDelta, rval))
                    .peek(newRoute -> rval.put(DonutLogicSupport.getLocationTag(newRoute.getCurrentEnd()), newRoute))
                    .collect(Collectors.toList());

            LoggingUtils.logMessage("DONUT", "Next recursive layer size: %d", nextLayer.size());
            currentLayer = nextLayer;
        }
        return new HashSet<>(rval.values());
    }

    public static Function<TravelRoute, Stream<TravelRoute>> buildNextLayerFunction(TimePoint startTime, TimeDelta maxDelta) {
        return route -> getAllPossibleStations(route.getCurrentEnd(), startTime.plus(route.getTotalTime()), maxDelta.minus(route
                .getTotalWalkTime()))
                .stream()
                .map(node -> route.clone().addNode(node));
    }

    /**
     * Get all possible stations directly travelable to from a given point. It does not calculate in-between stops.
     *
     * @param center    the point to start from
     * @param startTime the time to start at
     * @param maxDelta  the maximum time to walk
     * @return a set of nodes representing traveling from center directly to the possible stations
     */
    public static Set<TravelRouteNode> getAllPossibleStations(LocationPoint center, TimePoint startTime, TimeDelta maxDelta) {
        if (!(center instanceof TransStation)) return getWalkableStations(center, maxDelta);
        TransStation station = (TransStation) center;

        Map<TransStation, TravelRouteNode> walkable = getWalkableStations(station, maxDelta).stream()
                .collect(StreamUtils.collectWithKeys(node -> (TransStation) node.getPt()));

        Map<TransStation, TravelRouteNode> arrivable = DonutLogicSupport.getAllChainsForStop(station).stream()
                .flatMap(station1 -> getArrivableStations(station1, startTime).stream())
                .collect(StreamUtils.collectWithKeys(node -> (TransStation) node.getPt()));

        Set<TransStation> allPossibleStations = new HashSet<>();
        allPossibleStations.addAll(walkable.keySet());
        allPossibleStations.addAll(arrivable.keySet());

        return allPossibleStations.stream()
                .distinct()
                .map(keyStation -> {
                    if (walkable.containsKey(keyStation) && !arrivable.containsKey(keyStation)) {
                        return walkable.get(keyStation);
                    } else return arrivable.get(keyStation);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Get all stations directly travelable to without walking.
     *
     * @param station   the station to start at
     * @param startTime the time to start at
     * @return nodes representing directly travelling from station to all possible stations
     */
    public static Set<TravelRouteNode> getArrivableStations(TransStation station, TimePoint startTime) {
        if (station.getChain() == null) return Collections.emptySet();

        LoggingUtils.logMessage("DonutWalkRange::getArrivableStations", "Starting method with arguments: %s, %s", station
                .toString(), startTime.toString());

        TimePoint trueStart = DonutLogicSupport.getStationWithSchedule(station).getNextArrival(startTime);

        TimeDelta waitTime = startTime.timeUntil(trueStart);

        Set<TravelRouteNode> rval = StationRetriever.getStations(null, -1, null, null, station.getChain(), null)
                .stream()
                .filter(fromChain -> !Arrays.equals(fromChain.getCoordinates(), station.getCoordinates()))
                .map(DonutLogicSupport::getStationWithSchedule)
                .map(fromChain -> new TravelRouteNode.Builder()
                        .setWaitTime(waitTime.getDeltaLong())
                        .setPoint(fromChain)
                        .setTravelTime(fromChain.getNextArrival(trueStart).getUnixTime() - trueStart.getUnixTime())
                        .build()
                )
                .collect(Collectors.toSet());
        LoggingUtils.logMessage("DonutWalkRange::getArrivableStations", "Returing %d stations.", rval.size());
        return rval;
    }

    /**
     * Get all stations within a certain walking time, independent of start time and arrival time.
     *
     * @param begin    the location to start at
     * @param maxDelta the maximum walking time
     * @return nodes representing walking to all possible stations
     */
    public static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimeDelta maxDelta) {
        return StationRetriever.getStations(
                begin.getCoordinates(),
                LocationUtils.timeToWalkDistance(maxDelta.getDeltaLong(), true),
                null,
                null,
                null,
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

    public static Predicate<TravelRoute> nextLayerFilter(TimeDelta maxDelta, Map<String, TravelRoute> currentRoutes) {
        return route -> {
            if (route.getTotalWalkTime().getDeltaLong() >= maxDelta.getDeltaLong())
                return false;
            if (DonutLogicSupport.isMiddleMan(route))
                return false; //TODO: Not bandaid the middleman issue
            TravelRoute currentInMap = currentRoutes.get(DonutLogicSupport.getLocationTag(route.getCurrentEnd()));
            if (currentInMap == null) return true;
            return currentInMap.getTotalTime().getDeltaLong() > route.getTotalTime().getDeltaLong();
        };
    }
}
