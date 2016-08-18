package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
        if (nextLayer.size() == 0) return;
        buildStationRouteList(nextLayer, startTime, maxDelta, collector);

    }

    private static List<TravelRoute> buildStationRouteListIterator(TravelRoute initial, TimeModel startTime, TimeModel maxDelta, Map<String, TravelRoute> collector) {
        long initialDelta = (long) initial.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
        if (initialDelta >= maxDelta.getUnixTimeDelta()) return Collections.EMPTY_LIST;

        TimeModel trueStart = startTime.addUnixTime(initialDelta);
        TimeModel deltaLeft = maxDelta.addUnixTime(-1 * initialDelta);

        Map<TransStation, Long> allPossibleStationsRaw = getAllPossibleStations(initial.getCurrentEnd(), trueStart, deltaLeft);

        //Filter so that we only have 1 station per coordinate.
        Set<String> newTags = new HashSet<>();
        Map<TransStation, Long> allPossibleStations = new ConcurrentHashMap<>();
        allPossibleStationsRaw.keySet().stream().forEach(possible -> {
            String tag = getLocationTag(possible);
            if (newTags.contains(tag)) return;
            allPossibleStations.put(possible, allPossibleStationsRaw.get(possible));
            newTags.add(tag);
        });

        List<TravelRoute> newRoutesList = addStationsToRoute(initial, allPossibleStations);
        Set<TravelRoute> newRoutes = new HashSet<>(newRoutesList);
        newRoutes.removeAll(collector.values());

        return newRoutes.stream()

                .distinct()

                //Filter things we have already
                .filter(route -> !collector.values().contains(route))

                //Filter routes that have gone over time
                .filter(route -> ((long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l)) < maxDelta.getUnixTimeDelta())

                // We should only have 1 path to get to any location -- the shortest.
                // We use a map to guarantee this and then filter all those with greater times and lengths
                // than the one already in the map.
                .filter(route -> {

                    String currentEndTag = getLocationTag(route.getCurrentEnd());

                    if (!collector.containsKey(currentEndTag)) return true;

                    long oldTime = (long) collector.get(currentEndTag).getCosts().getOrDefault(TIME_DELTA_TAG, 0l);
                    long newTime = (long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l);

                    if (oldTime != newTime) return oldTime > newTime;

                    long oldLength = collector.get(currentEndTag).getRoute().size();
                    long newLength = route.getRoute().size();
                    return oldLength > newLength;

                })

                //Finally, collect and recurse.
                .map(route -> {
                    collector.put(getLocationTag(route.getCurrentEnd()), route);
                    return route;
                })
                .collect(Collectors.toList());
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
        Map<TransStation, Long> arrivable = getAllChainsForStop(station).stream()
                .map(station1 -> getArrivableStations(station1, startTime, maxDelta))
                .reduce(new ConcurrentHashMap<>(), (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                });
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
        List<TransStation> stationsInRange = getStations(begin.getCoordinates(), range, null, null);
        return getWalkTimes(begin, stationsInRange);
    }

    public static List<TravelRoute> addStationsToRoute(TravelRoute initial, Map<TransStation, Long> stationsToDeltas) {
        return stationsToDeltas.keySet().stream()
                .map(station -> {
                    TravelRoute newRoute = initial.clone();
                    newRoute.addStation(station);
                    addTimeToRoute(newRoute, stationsToDeltas.get(station));
                    return newRoute;
                })
                .collect(Collectors.toList());
    }

    public static Map<TransStation, Long> getArrivableStations(TransStation station, TimeModel startTime, TimeModel maxDelta) {

        Map<TransStation, Long> rval = new ConcurrentHashMap<>();
        if (station.getChain() == null) return rval;
        if (station.getSchedule() == null || station.getSchedule().isEmpty()){
            LoggingUtils.logMessage("DONUT","Station has no schedule. Retrying query.");
            List<TransStation> trueStation = StationRetriever.getStations(station.getCoordinates(), 0.001,station.getChain(), null);
            if (trueStation.size() != 1 || trueStation.get(0).getSchedule() == null || trueStation.get(0).getSchedule().isEmpty()){
                LoggingUtils.logError("DONUT", "Error in requery.\nData:\nList size: %d\nTrueStation: %s",
                        trueStation.size(),
                        (!trueStation.isEmpty()) ? trueStation.get(0).toString() : "Null");
            }
        }

        TimeModel trueStart = station.getNextArrival(startTime);
        if (trueStart.getUnixTime() < startTime.getUnixTime()) {
            String errorMsg = "";
            errorMsg += String.format("Truestart is %d, but startTime is %d, %d more than that.\n",
                    trueStart.getUnixTime(), startTime.getUnixTime(), startTime.getUnixTime() - trueStart.getUnixTime());
            errorMsg += String.format("Data:\nTrueStart: %s\nStartTime: %s",trueStart.toString(), startTime.toString());
            LoggingUtils.logError("DONUT", errorMsg);
            return new HashMap<>();
        }

        List<TransStation> inChain = StationRetriever.getStations(null, 0, station.getChain(), null);
        inChain.stream().forEach(fromChain -> {
            if (Arrays.equals(fromChain.getCoordinates(), station.getCoordinates())) return;
            if (fromChain.getSchedule() == null || fromChain.getSchedule().isEmpty()){
                LoggingUtils.logMessage("DONUT","Station has no schedule. Retrying query.");
                List<TransStation> trueStation = StationRetriever.getStations(fromChain.getCoordinates(), 0.001,fromChain.getChain(), null);
                if (trueStation.size() != 1 || trueStation.get(0).getSchedule() == null || trueStation.get(0).getSchedule().isEmpty()){
                    LoggingUtils.logError("DONUT", "Error in requery.\nData:\nList size: %d\nTrueStation: %s",
                            trueStation.size(),
                            (!trueStation.isEmpty()) ? trueStation.get(0).toString() : "Null");
                }
            }
            TimeModel arriveTime = fromChain.getNextArrival(trueStart);
            if (arriveTime.getUnixTime() < trueStart.getUnixTime()) {
                String errorMsg = "";
                errorMsg += String.format("ArriveTime is %d, but trueStart is %d, %d more than that.\n\n",
                        arriveTime.getUnixTime(), startTime.getUnixTime(), startTime.getUnixTime() - arriveTime.getUnixTime());
                errorMsg += String.format("Time Data:\nArrivetime: %s\nStarttime: %s\n TrueStart: %s\nMaxDelta: %s\n\n",
                        arriveTime.toString(), startTime.toString(), trueStart.toString(), maxDelta.toString());

                errorMsg += String.format("Station Data:\nStation: %s\nChain: %s\nFromChain: %s", station.toString(), station.getChain().toString(), fromChain.toString());
                LoggingUtils.logMessage("DONUT", errorMsg);
                return;
            }

            long timeFromStart = arriveTime.getUnixTime() - startTime.getUnixTime();
            if (timeFromStart > maxDelta.getUnixTimeDelta()) return;
            rval.put(fromChain, timeFromStart);
        });
        return rval;
    }

    public static Set<TransStation> getAllChainsForStop(TransStation orig) {
        List<TransStation> all = StationRetriever.getStations(orig.getCoordinates(), 0, null, null);
        Set<TransStation> rval = new HashSet<>(all);
        rval.add(orig);
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

    public static String getLocationTag(LocationPoint pt) {
        return pt.getCoordinates()[0] + ", " + pt.getCoordinates()[1];
    }
}
