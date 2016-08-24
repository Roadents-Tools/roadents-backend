package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;
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
 * Created by ilan on 7/27/16.
 */
public class DonutLogicSupport {

    public static final String TIME_DELTA_TAG = "timedelta";

    public static void buildStationRouteList(Collection<? extends TravelRoute> currentLayer, TimeModel startTime, TimeModel maxDelta, Map<String, TravelRoute> collector) {
        List<TravelRoute> nextLayer = currentLayer.stream()
                .flatMap(route -> buildStationRouteListIterator(route, startTime, maxDelta, collector).stream())
                .collect(Collectors.toList());
        LoggingUtils.logMessage("DONUT", "Next recursive layer size: " + nextLayer.size());
        if (nextLayer.size() == 0) {

            return;
        }
        buildStationRouteList(nextLayer, startTime, maxDelta, collector);

    }

    private static List<TravelRoute> buildStationRouteListIterator(TravelRoute initial, TimeModel startTime, TimeModel maxDelta, Map<String, TravelRoute> collector) {
        long initialDelta = initial.getTotalTime();
        if (initialDelta >= maxDelta.getUnixTimeDelta()) return Collections.EMPTY_LIST;

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

                    if (!collector.containsKey(currentEndTag)) return true;

                    long oldTime = collector.get(currentEndTag).getTotalTime();
                    long newTime = route.getTotalTime();

                    if (oldTime != newTime) return oldTime > newTime;

                    long oldLength = collector.get(currentEndTag).getRoute().size();
                    long newLength = route.getRoute().size();
                    return oldLength > newLength;

                })
                .map(route -> {
                    collector.put(getLocationTag(route.getCurrentEnd()), route);
                    return route;
                })
                .collect(Collectors.toList());
    }

    public static Set<TravelRouteNode> filterAllPossibleStations(Set<TravelRouteNode> raw) {
        Map<String, TravelRouteNode> newTags = new ConcurrentHashMap<>();
        Set<TravelRouteNode> rval = Sets.newConcurrentHashSet();
        raw.stream().forEach(possible -> {
            String tag = getLocationTag(possible.getPt());

            if (!newTags.containsKey(tag)) {
                rval.add(possible);
                newTags.put(tag, possible);
                return;
            }

            TravelRouteNode old = newTags.get(tag);
            long oldTime = old.getTotalTimeToArrive();
            if (oldTime < possible.getTotalTimeToArrive()) return;

            rval.add(possible);
            newTags.put(tag, possible);
        });
        return rval;
    }

    public static Set<TravelRouteNode> getWalkableDestinations(LocationPoint center, TimeModel maxDelta, LocationType type) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<DestinationLocation> possibleDests = LocationRetriever.getLocations(center.getCoordinates(), range, type, null);
        return getWalkTimes(center, possibleDests);
    }

    public static Set<TravelRouteNode> getWalkTimes(LocationPoint begin, Collection<? extends LocationPoint> points) {
        Set<TravelRouteNode> rval = Sets.newConcurrentHashSet();
        points.stream().forEach(point -> {
            double dist = LocationUtils.distanceBetween(begin.getCoordinates(), point.getCoordinates(), true);
            long time = LocationUtils.distanceToWalkTime(dist, true);
            TravelRouteNode node = new TravelRouteNode.Builder().setWalkTime(time).setPoint(point).build();
            rval.add(node);
        });
        return rval;
    }


    public static Set<TravelRouteNode> getAllPossibleStations(LocationPoint center, TimeModel startTime, TimeModel maxDelta) {
        if (!(center instanceof TransStation)) return getWalkableStations(center, maxDelta);
        TransStation station = (TransStation) center;

        Map<TransStation, TravelRouteNode> walkable = getWalkableStations(station, maxDelta).stream()
                .reduce(new ConcurrentHashMap<>(), (map, node) -> {
                    map.put((TransStation) node.getPt(), node);
                    return map;
                }, (map, map2) -> {
                    map.putAll(map2);
                    return map;
                });

        Map<TransStation, TravelRouteNode> arrivable = getAllChainsForStop(station).stream()
                .map(station1 -> getArrivableStations(station1, startTime, maxDelta))
                .flatMap(Collection::stream)
                .reduce(new ConcurrentHashMap<>(), (map, node) -> {
                    map.put((TransStation) node.getPt(), node);
                    return map;
                }, (map, map2) -> {
                    map.putAll(map2);
                    return map;
                });

        Map<TransStation, TravelRouteNode> rval = new ConcurrentHashMap<>(walkable);

        arrivable.keySet().stream().forEach(keyStation -> {
            if (walkable.containsKey(keyStation)) {
                long walkableDelta = walkable.get(keyStation).getTotalTimeToArrive();
                long arrivableDelta = arrivable.get(keyStation).getTotalTimeToArrive();
                TravelRouteNode min = (walkableDelta > arrivableDelta) ? arrivable.get(keyStation) : walkable.get(keyStation);
                rval.put(keyStation, min);
            } else {
                rval.put(keyStation, arrivable.get(keyStation));
            }
        });
        return Sets.newConcurrentHashSet(rval.values());
    }

    public static Set<TravelRouteNode> getWalkableStations(LocationPoint begin, TimeModel maxDelta) {
        long deltaUnix = maxDelta.getUnixTimeDelta();
        double range = LocationUtils.timeToWalkDistance(deltaUnix, true);
        List<TransStation> stationsInRange = getStations(begin.getCoordinates(), range, null, null);
        return getWalkTimes(begin, stationsInRange);
    }

    public static List<TravelRoute> addStationsToRoute(TravelRoute initial, Set<TravelRouteNode> stationsToDeltas) {
        return stationsToDeltas.stream()
                .map(node -> {
                    TravelRoute newRoute = initial.clone();
                    newRoute.addNode(node);
                    return newRoute;
                })
                .collect(Collectors.toList());
    }

    public static Set<TravelRouteNode> getArrivableStations(TransStation station, TimeModel startTime, TimeModel maxDelta) {

        Set<TravelRouteNode> rval = Sets.newConcurrentHashSet();
        if (station.getChain() == null) return rval;


        TransStation stationWithSchedule = getStationWithSchedule(station);

        TimeModel trueStart = stationWithSchedule.getNextArrival(startTime);
        long waitTime = trueStart.getUnixTime() - startTime.getUnixTime();

        List<TransStation> inChain = StationRetriever.getStations(null, 0, stationWithSchedule.getChain(), null);

        inChain.stream().forEach(fromChain -> {

            TravelRouteNode.Builder builder = new TravelRouteNode.Builder();
            builder.setWaitTime(waitTime);
            builder.setPoint(fromChain);

            if (Arrays.equals(fromChain.getCoordinates(), stationWithSchedule.getCoordinates())) return;
            fromChain = getStationWithSchedule(fromChain);
            TimeModel arriveTime = fromChain.getNextArrival(trueStart);


            long travelTime = arriveTime.getUnixTime() - trueStart.getUnixTime();
            if (travelTime + waitTime > maxDelta.getUnixTimeDelta()) return;
            builder.setTravelTime(travelTime);

            rval.add(builder.build());
        });
        return rval;
    }

    public static TransStation getStationWithSchedule(TransStation station) {
        if (!(station.getSchedule() == null || station.getSchedule().isEmpty())) return station;

        LoggingUtils.logMessage("DONUT", "Station has no schedule. Retrying query.");
        List<TransStation> trueStation = StationRetriever.getStations(station.getCoordinates(), 0.001, station.getChain(), null);
        if (trueStation.size() != 1 || trueStation.get(0).getSchedule() == null || trueStation.get(0).getSchedule().isEmpty()) {
            LoggingUtils.logError("DONUT", "Error in requery.\nData:\nList size: %d\nTrueStation: %s",
                    trueStation.size(),
                    (!trueStation.isEmpty()) ? trueStation.get(0).toString() : "Null");
            return station;
        }
        return trueStation.get(0);
    }

    public static Set<TransStation> getAllChainsForStop(TransStation orig) {
        List<TransStation> all = StationRetriever.getStations(orig.getCoordinates(), 0, null, null);
        Set<TransStation> rval = new HashSet<>(all);
        rval.add(orig);
        return rval;
    }

    public static List<TravelRoute> addDestinationsToRoute(TravelRoute initial, Set<TravelRouteNode> destsToDeltas) {
        return destsToDeltas.stream()
                .map(dest -> {
                    TravelRoute newRoute = initial.clone();
                    newRoute.setDestinationNode(dest);
                    return newRoute;
                }).collect(Collectors.toList());
    }

    public static String getLocationTag(LocationPoint pt) {
        return pt.getCoordinates()[0] + ", " + pt.getCoordinates()[1];
    }
}
