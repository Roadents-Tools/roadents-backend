package com.reroute.backend.logic.pathmaker;

import com.google.common.collect.Sets;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.generator.GeneratorSupport;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * Created by ilan on 7/10/16.
 */
public class PathmakerCore implements LogicCore {

    public static final String BEST_TAG = "DONUTAB_BEST";
    public static final String MULTI_TAG = "DONUTAB_MULTI";


    @Override
    public ApplicationResult performLogic(ApplicationRequest args) {

        //Get the args
        TimePoint startTime = args.getStartTime();
        boolean bestonly = BEST_TAG.equals(args.getTag());
        List<DestinationLocation> ends = args.getEnds();
        StartPoint st = args.getStarts().get(0);

        //Run the core
        if (bestonly) {
            List<TravelRoute> destsToRoutes = runDonutRouting(st, startTime, ends);
            return ApplicationResult.ret(destsToRoutes).withErrors(LoggingUtils.getErrors());

        } else {
            List<TravelRoute> flattened = buildAllRoutesFrom(st, ends, startTime).values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            return ApplicationResult.ret(flattened).withErrors(LoggingUtils.getErrors());
        }
    }

    @Override
    public Set<String> getTags() {
        return Sets.newHashSet(BEST_TAG, MULTI_TAG);
    }

    @Override
    public boolean isValid(ApplicationRequest request) {
        return request.getStarts() != null && !request.getStarts().isEmpty() && request.getStarts().size() == 1
                && request.getEnds() != null && !request.getEnds().isEmpty()
                && request.getStartTime() != null && !TimePoint.NULL.equals(request.getStartTime())
                && request.getQuery() != null;
    }

    public static List<TravelRoute> runDonutRouting(StartPoint start, TimePoint startTime, List<DestinationLocation> ends) {
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
