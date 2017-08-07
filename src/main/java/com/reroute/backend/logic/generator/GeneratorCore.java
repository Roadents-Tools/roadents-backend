package com.reroute.backend.logic.generator;

import com.google.common.collect.Lists;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.utils.LogicUtils;
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Created by ilan on 7/10/16.
 */
public class GeneratorCore implements LogicCore {

    public static final String TAG = "DONUT";

    @Override
    public ApplicationResult performLogic(ApplicationRequest args) {

        StartPoint pt = args.getStarts().get(0);
        TimePoint startTime = args.getStartTime();
        TimeDelta maxTimeDelta = args.getMaxDelta();


        StationRetriever.prepareWorld(pt, startTime, maxTimeDelta);

        //Get the station routes
        Set<TravelRoute> stationRoutes = LogicUtils.buildStationRouteList(pt, startTime, maxTimeDelta, args.getResultsFilter());
        LoggingUtils.logMessage(getClass().getName(), "Got %d station routes.", stationRoutes.size());

        //Get the raw dest routes
        Set<TravelRoute> destRoutes = stationRoutes.stream()
                .filter(route -> maxTimeDelta.getDeltaLong() >= route.getTotalTime().getDeltaLong())
                .flatMap(route -> getWalkableDestinations(
                        route.getCurrentEnd(),
                        maxTimeDelta.minus(route.getTotalTime()),
                        args.getQuery()
                        ).stream()
                        .map(node -> route.clone().setDestinationNode(node))
                )
                .collect(Collectors.toSet());

        LoggingUtils.logMessage(
                getClass().getName(),
                "Got %d -> %d dest routes.", stationRoutes.size(), destRoutes.size()
        );


        //Optimize the routes, and do some just-in-case filtering against some historic errors that may or may no longer
        //be valid.
        Map<DestinationLocation, TravelRoute> destToShortest = destRoutes.stream()
                .filter(rt -> !LogicUtils.isMiddleMan(rt))
                .filter(rt -> !LogicUtils.isFlash(rt))
                .collect(LogicUtils.OPTIMAL_ROUTES_FOR_DESTINATIONS);

        LoggingUtils.logMessage(getClass().getName(),
                "Got %d -> %d filtered routes. Of those, %d are nonzero degree.",
                destRoutes.size(), destToShortest.size(),
                destToShortest.values().stream().map(TravelRoute::getRoute).filter(route -> route.size() > 2).count()
        );

        //Build the output
        return ApplicationResult.ret(Lists.newArrayList(destToShortest.values()))
                .withErrors(LoggingUtils.getErrors());
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
        return LocationRetriever.getLocations(center, LocationUtils.timeToWalkDistance(maxDelta), type, null)
                .stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(center, point).getDeltaLong())
                        .setPoint(point)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton(TAG);
    }

    @Override
    public boolean isValid(ApplicationRequest request) {
        return request.getStarts() != null && !request.getStarts().isEmpty() && request.getStarts().size() == 1
                && request.getStartTime() != null && !TimePoint.NULL.equals(request.getStartTime())
                && request.getMaxDelta() != null && !TimeDelta.NULL.equals(request.getMaxDelta())
                && request.getQuery() != null;
    }


}
