package com.reroute.backend.logic.pathmaker;

import com.reroute.backend.logic.generator.GeneratorSupport;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * Created by ilan on 7/10/16.
 */
public class PathmakerCore implements LogicCore {

    public static final String TAG = "DONUTAB";
    public static final String START_TIME_TAG = "starttime";
    public static final String A_LAT_TAG = "latitude";
    public static final String A_LONG_TAG = "longitude";
    public static final String B_LAT_TAG_FORMAT = "latitude%d";
    public static final String B_LONG_TAG = "longitude%d";
    public static final String BEST_ONLY_TAG = "bestonly";

    public static final String ROUTE_LIST_TAG = "ROUTES";

    private static final LocationType FILLER_TYPE = new LocationType("Destination", "Destination");

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        //Get the args
        long startUnixTime = (long) args.get(START_TIME_TAG);
        TimePoint startTime = new TimePoint(startUnixTime, "America/Los_Angeles");
        double startLat = (double) args.get(A_LAT_TAG);
        double startLong = (double) args.get(A_LONG_TAG);
        boolean bestonly = Optional.ofNullable(args.get(BEST_ONLY_TAG))
                .map(obj -> Boolean.parseBoolean(obj.toString()))
                .orElse(true);


        List<DestinationLocation> ends = new ArrayList<>();
        for (int i = 2; args.containsKey(String.format(B_LAT_TAG_FORMAT, i)); i++) {
            double curLat = (double) args.get(String.format(B_LAT_TAG_FORMAT, i));
            double curLng = (double) args.get(String.format(B_LONG_TAG, i));
            ends.add(new DestinationLocation(
                    String.format("end %d", i),
                    FILLER_TYPE,
                    new double[] { curLat, curLng }
            ));
        }

        StartPoint st = new StartPoint(new double[] { startLat, startLong });
        //Run the core
        if (bestonly) {
            List<TravelRoute> destsToRoutes = runDonutRouting(st, startTime, ends);

            //Build the output
            Map<String, List<Object>> output = new HashMap<>();
            if (LoggingUtils.hasErrors()) {
                List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
                output.put("ERRORS", errs);
            }
            output.put(ROUTE_LIST_TAG, new ArrayList<>(destsToRoutes));
            return output;
        } else {
            Map<DestinationLocation, List<TravelRoute>> rval = buildAllRoutesFrom(st, ends, startTime);

            Map<String, List<Object>> output = new HashMap<>();
            if (LoggingUtils.hasErrors()) {
                List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
                output.put("ERRORS", errs);
            }
            rval.forEach((key, value) -> output.put(key.toString(), new ArrayList<Object>(value)));
            return output;
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    public static List<TravelRoute> runDonutRouting(StartPoint start, TimePoint startTime, List<DestinationLocation> ends) {

        //Prepare the call
        if (start == null || startTime == null || ends == null || ends.isEmpty()) {
            return Collections.emptyList();
        }

        Map<DestinationLocation, List<TravelRoute>> endsToRoutes = buildAllRoutesFrom(start, ends, startTime);
        return ends.stream()
                .map(end -> endsToRoutes.get(end)
                        .stream()
                        .min(Comparator.comparing(rt -> rt.getTotalTime().getDeltaLong()))
                        .get()
                )
                .collect(Collectors.toList());
    }

    public static Map<DestinationLocation, List<TravelRoute>> buildAllRoutesFrom(StartPoint start, List<DestinationLocation> ends, TimePoint startTime) {

        //Prepare the call
        if (start == null || startTime == null || ends == null || ends.isEmpty()) {
            return Collections.emptyMap();
        }

        TimeDelta maxTimeDelta = ends.stream()
                .map(end -> LocationUtils.timeBetween(start, end))
                .max(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(TimeDelta.NULL);

        StationRetriever.prepareWorld(start, startTime, maxTimeDelta);

        Predicate<TravelRoute> isInAnyRange = ends.stream()
                .map(end -> isRouteInRange(end, maxTimeDelta))
                .reduce(Predicate::or)
                .orElse(rt -> false); //If the predicate is null, then we have no ends; filter everything immediately.


        //Get the station routes
        Set<TravelRoute> stationRoutes = GeneratorSupport.buildStationRouteList(start, startTime, maxTimeDelta, isInAnyRange);
        LoggingUtils.logMessage(PathmakerCore.class.getName(), "Got %d station routes.", stationRoutes.size());

        //Optimize and attach the ends
        Map<DestinationLocation, List<TravelRoute>> endsToRoutes = new HashMap<>();
        for (DestinationLocation end : ends) {
            List<TravelRoute> allRoutes = stationRoutes.stream()
                    .filter(route -> LocationUtils.timeBetween(route.getCurrentEnd(), end)
                            .getDeltaLong() <= LocationUtils.timeBetween(start, end).getDeltaLong())
                    .map(base -> base.clone().setDestinationNode(new TravelRouteNode.Builder()
                            .setPoint(end)
                            .setWalkTime(LocationUtils.timeBetween(base.getCurrentEnd(), end)
                                    .getDeltaLong())
                            .build()
                    ))
                    .collect(Collectors.toList());
            endsToRoutes.put(end, allRoutes);
        }
        return endsToRoutes;
    }

    private static Predicate<TravelRoute> isRouteInRange(LocationPoint end, TimeDelta maxDelta) {
        if (null == maxDelta || TimeDelta.NULL.equals(maxDelta)) return route -> false;
        return route -> {
            TimeDelta left = maxDelta.minus(route.getTotalTime());
            Distance maxDistance = LocationUtils.timeToMaxTransit(left);
            return LocationUtils.distanceBetween(end, route.getCurrentEnd()).inMeters() <= maxDistance.inMeters();
        };
    }
}
