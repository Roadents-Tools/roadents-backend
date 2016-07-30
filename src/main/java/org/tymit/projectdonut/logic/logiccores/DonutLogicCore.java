package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.model.*;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.tymit.projectdonut.logic.logiccores.DonutLogicSupport.TIME_DELTA_TAG;


/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCore implements LogicCore {

    private static String TAG = "DONUT";
    private static String START_TIME_TAG = "starttime";
    private static String LAT_TAG = "latitude";
    private static String LONG_TAG = "longitude";
    private static String TYPE_TAG = "type";

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

        //Run the core
        Map<DestinationLocation, TravelRoute> destsToRoutes = runDonut(
                new StartPoint(new double[]{startLat, startLong}),
                startTime,
                maxTimeDelta,
                type
        );

        //Build the output
        Map<String, List<Object>> output = new HashMap<>();
        if (LoggingUtils.hasErrors()) {
            List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
            output.put("ERRORS", errs);
        }
        output.put("DESTS", new ArrayList<>());
        output.put("ROUTES", new ArrayList<>());
        for (TravelRoute route : destsToRoutes.values()) {
            output.get("DESTS").add(route.getDestination());
            output.get("ROUTES").add(route);
        }
        return output;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private Map<DestinationLocation, TravelRoute> runDonut(StartPoint center, TimeModel startTime, TimeModel maxTimeDelta, LocationType type) {
        TravelRoute startRoute = new TravelRoute(center, startTime);

        Map<LocationPoint, TravelRoute> stationRoutes = new ConcurrentHashMap<>();
        stationRoutes.put(center, startRoute);
        DonutLogicSupport.buildStationRouteList(startRoute, startTime, maxTimeDelta, stationRoutes);

        Set<TravelRoute> destRoutes = stationRoutes.values().stream()
                .filter(route -> maxTimeDelta.getUnixTimeDelta() >= (long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l))
                .flatMap(route -> {
                    TimeModel trueDelta = maxTimeDelta.addUnixTime((long) route.getCosts().getOrDefault(TIME_DELTA_TAG, 0l));
                    Map<DestinationLocation, Long> possibleDests = DonutLogicSupport.getWalkableDestinations(route.getCurrentEnd(), trueDelta, type);
                    return DonutLogicSupport.addDestinationsToRoute(route, possibleDests).stream();
                })
                .collect(Collectors.toSet());

        ConcurrentMap<DestinationLocation, TravelRoute> destToShortest = new ConcurrentHashMap<>();
        destRoutes.stream()
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

        return destToShortest;
    }
}
