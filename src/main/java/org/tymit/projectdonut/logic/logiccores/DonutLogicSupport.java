package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.tymit.projectdonut.stations.StationRetriever.getStations;

/**
 * Static utilities for the DonutLogicCore.
 */
public final class DonutLogicSupport {

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
    public static Set<TravelRoute> buildStationRouteList(StartPoint initialPoint, TimeModel startTime, TimeModel maxDelta) {
        Map<String, TravelRoute> rval = new ConcurrentHashMap<>();
        TravelRoute startRoute = new TravelRoute(initialPoint, startTime);
        rval.put(getLocationTag(initialPoint), startRoute);
        buildStationRouteListRepeater(Sets.newHashSet(startRoute), startTime, maxDelta, rval);
        return Sets.newHashSet(rval.values());
    }

    /**
     * The recursive repeater for buildStationRouteList.
     * It runs the iterator, collects the result, logs it, and then aggregates before recursing.
     *
     * @param currentLayer the output from the previous call to buildStationRouteListRepeater
     * @param startTime    the startTime to use
     * @param maxDelta     the maxDelta to use
     * @param collector    where the routes from all iterations are collected, in the form of location tag -> route
     */
    private static void buildStationRouteListRepeater(Collection<? extends TravelRoute> currentLayer, TimeModel startTime, TimeModel maxDelta, Map<String, TravelRoute> collector) {
        List<TravelRoute> nextLayer = currentLayer.stream()
                .flatMap(route -> buildStationRouteListIterator(route, startTime, maxDelta, collector).stream())
                .collect(Collectors.toList());
        LoggingUtils.logMessage("DONUT", "Next recursive layer size: " + nextLayer.size());
        if (nextLayer.size() == 0) return;
        buildStationRouteListRepeater(nextLayer, startTime, maxDelta, collector);
    }

    /**
     * This method contains most of the actual logic behind buildStationRouteList.
     * It is called for every member of the current layer for each call to buildStationRouteListRepeater.
     *
     * @param initial   the route to find new stations for
     * @param startTime the startTime to use
     * @param maxDelta  the maxDelta to use
     * @param collector the aggregator to use
     * @return the list of routes to new stations to use
     */
    private static List<TravelRoute> buildStationRouteListIterator(TravelRoute initial, TimeModel startTime, TimeModel maxDelta, Map<String, TravelRoute> collector) {
        long initialDelta = initial.getTotalTime();
        if (initialDelta >= maxDelta.getUnixTimeDelta()) return Collections.emptyList();

        TimeModel trueStart = startTime.addUnixTime(initialDelta);
        TimeModel deltaLeft = maxDelta.addUnixTime(-1 * initialDelta);

        Set<TravelRouteNode> allPossibleStationsRaw = getAllPossibleStations(initial.getCurrentEnd(), trueStart, deltaLeft);

        //Filter so that we only have 1 station per coordinate.
        Set<TravelRouteNode> allPossibleStations = filterAllPossibleStations(allPossibleStationsRaw);
        List<TravelRoute> newRoutesList = addStationsToRoute(initial, allPossibleStations);
        Set<TravelRoute> newRoutes = new HashSet<>(newRoutesList);
        newRoutes.removeAll(collector.values());

        return newRoutes.stream()
                .filter(route -> !collector.values().contains(route))
                .filter(route -> route.getTotalTime() < maxDelta.getUnixTimeDelta())
                .filter(route -> {
                    String currentEndTag = getLocationTag(route.getCurrentEnd());
                    TravelRoute currentMap = collector.get(currentEndTag);
                    return currentMap == null ||
                            currentMap.getTotalTime() > route.getTotalTime() ||
                            currentMap.getTotalTime() == route.getTotalTime() && currentMap.getRoute()
                                    .size() > route.getRoute().size();
                })
                .map(route -> {
                    collector.put(getLocationTag(route.getCurrentEnd()), route);
                    return route;
                })
                .collect(Collectors.toList());
    }

    /**
     * Filter a group of nodes so that we only have the best route to any single location.
     *
     * @param raw a set of nodes that may have duplicate destination
     * @return the filtered nodes
     */
    public static Set<TravelRouteNode> filterAllPossibleStations(Set<TravelRouteNode> raw){
        Map<String, TravelRouteNode> newTags = new ConcurrentHashMap<>();
        raw.stream()
                .filter(node -> newTags.putIfAbsent(getLocationTag(node.getPt()), node) != null)
                .filter(node -> node.getTotalTimeToArrive() < newTags.get(getLocationTag(node.getPt()))
                        .getTotalTimeToArrive())
                .forEach(node -> newTags.put(getLocationTag(node.getPt()), node));

        return new HashSet<>(newTags.values());
    }

    /**
     * Get all the destinations walkable in a given area.
     * @param center the center of the area
     * @param maxDelta the maximum time we will walk
     * @param type the type of destination to find
     * @return the found destinations
     */
    public static Set<TravelRouteNode> getWalkableDestinations(LocationPoint center, TimeModel maxDelta, LocationType type) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<DestinationLocation> possibleDests = LocationRetriever.getLocations(center.getCoordinates(), range, type, null);
        return getWalkTimes(center, possibleDests);
    }

    /**
     * Get the walk times between a center and a list of points.
     * @param begin the center to check all other points against
     * @param points the collection of all points to walk to
     * @return a collection of nodes representing walking from begin to each point in points
     */
    public static Set<TravelRouteNode> getWalkTimes(LocationPoint begin, Collection<? extends LocationPoint> points) {
        return points.parallelStream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(begin.getCoordinates(), point.getCoordinates()))
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    /**
     * Get all possible stations directly travelable to from a given point. It does not calculate in-between stops.
     * @param center the point to start from
     * @param startTime the time to start at
     * @param maxDelta the maximum time to travel
     * @return a set of nodes representing traveling from center directly to the possible stations
     */
    public static Set<TravelRouteNode> getAllPossibleStations(LocationPoint center, TimeModel startTime, TimeModel maxDelta) {
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

        return allPossibleStations.parallelStream()
                .map(keyStation -> {
                    if (walkable.containsKey(keyStation) && !arrivable.containsKey(keyStation))
                        return walkable.get(keyStation);
                    if (!walkable.containsKey(keyStation) && arrivable.containsKey(keyStation))
                        return arrivable.get(keyStation);
                    if (walkable.get(keyStation).getTotalTimeToArrive() <= arrivable.get(keyStation)
                            .getTotalTimeToArrive()) return walkable.get(keyStation);
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
    public static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimeModel maxDelta) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<TransStation> stationsInRange = getStations(begin.getCoordinates(), range, null, null);
        return getWalkTimes(begin, stationsInRange);
    }

    /**
     * Add a set of stations to an initial base route.
     *
     * @param initial  the base route to use
     * @param stations the new nodes to add
     * @return clones of initial with the addition of each node in stations
     */
    public static List<TravelRoute> addStationsToRoute(TravelRoute initial, Set<TravelRouteNode> stations) {
        return stations.parallelStream()
                .map(node -> initial.clone().addNode(node))
                .collect(Collectors.toList());
    }

    /**
     * Get all stations directly travelable to within a given time.
     * @param station the station to start at
     * @param startTime the time to start at
     * @param maxDelta the maximum time to travel
     * @return nodes representing directly travelling from station to all possible stations
     */
    public static Set<TravelRouteNode> getArrivableStations(TransStation station, TimeModel startTime, TimeModel maxDelta) {
        if (station.getChain() == null) return Collections.emptySet();

        TimeModel trueStart = getStationWithSchedule(station).getNextArrival(startTime);
        long waitTime = trueStart.getUnixTime() - startTime.getUnixTime();

        return StationRetriever.getStations(null, 0, station.getChain(), null).parallelStream()
                .filter(fromChain -> !Arrays.equals(fromChain.getCoordinates(), station.getCoordinates()))
                .map(DonutLogicSupport::getStationWithSchedule)
                .filter(fromChain -> fromChain.getNextArrival(trueStart)
                        .getUnixTime() - trueStart.getUnixTime() + waitTime <= maxDelta.getUnixTimeDelta())
                .map(fromChain -> new TravelRouteNode.Builder()
                        .setWaitTime(waitTime)
                        .setPoint(fromChain)
                        .setTravelTime(fromChain.getNextArrival(trueStart).getUnixTime() - trueStart.getUnixTime())
                        .build())
                .collect(Collectors.toSet());
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

    /**
     * Add destinations to a given base route.
     *
     * @param initial the base route to use
     * @param dests   nodes representing traveling from the end of initial to a given destination
     * @return a list of new routes representing traveling along initial and then going to each destination in dests
     */
    public static List<TravelRoute> addDestinationsToRoute(TravelRoute initial, Set<TravelRouteNode> dests) {
        return dests.stream()
                .map(dest -> initial.clone().setDestinationNode(dest))
                .collect(Collectors.toList());
    }

    /**
     * Get a tag to represent a single location, for filtering purposes.
     * @param pt the location
     * @return the location's tag
     */
    public static String getLocationTag(LocationPoint pt) {
        return pt.getCoordinates()[0] + ", " + pt.getCoordinates()[1];
    }
}
