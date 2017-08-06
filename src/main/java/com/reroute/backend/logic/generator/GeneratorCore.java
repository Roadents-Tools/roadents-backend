package com.reroute.backend.logic.generator;

import com.google.common.collect.Lists;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
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

        //Get the args
        StartPoint pt = args.getStarts().get(0);
        TimePoint startTime = args.getStartTime();
        TimeDelta maxTimeDelta = args.getMaxDelta();
        LocationType type = args.getQuery();

        //Run the core
        Map<DestinationLocation, TravelRoute> destsToRoutes = runDonut(
                pt,
                startTime,
                maxTimeDelta,
                type
        );

        //Build the output
        return ApplicationResult.ret(Lists.newArrayList(destsToRoutes.values()))
                .withErrors(LoggingUtils.getErrors());
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

    /**
     * Runs the generator logic core.
     *
     * @param center       the starting central location to use
     * @param startTime    the time to start at
     * @param maxTimeDelta the maximum time for each route
     * @param type         the type of destination to find
     * @return all possible destinations mapped to the best route to get there
     */
    public Map<DestinationLocation, TravelRoute> runDonut(StartPoint center, TimePoint startTime, TimeDelta maxTimeDelta, LocationType type) {

        StationRetriever.prepareWorld(center, startTime, maxTimeDelta);

        //Get the station routes
        Set<TravelRoute> stationRoutes = GeneratorSupport.buildStationRouteList(center, startTime, maxTimeDelta);
        LoggingUtils.logMessage(getClass().getName(), "Got %d station routes.", stationRoutes.size());

        //Get the raw dest routes
        Set<TravelRoute> destRoutes = stationRoutes.stream()
                .filter(route -> maxTimeDelta.getDeltaLong() >= route.getTotalTime()
                        .getDeltaLong())
                .flatMap(route -> GeneratorSupport.getWalkableDestinations(route
                        .getCurrentEnd(), maxTimeDelta.minus(route.getTotalTime()), type)
                        .stream()
                        .map(node -> route.clone().setDestinationNode(node))
                )
                .collect(Collectors.toSet());
        LoggingUtils.logMessage(getClass().getName(), "Got %d -> %d dest routes.", stationRoutes
                .size(), destRoutes.size());

        //Filter and correct the dest routes
        Map<DestinationLocation, TravelRoute> destToShortest = destRoutes.stream()
                .filter(rt -> !GeneratorSupport.isMiddleMan(rt))
                .filter(rt -> !GeneratorSupport.isFlash(rt))
                .collect(GeneratorSupport.OPTIMAL_ROUTES_FOR_DESTINATIONS);

        LoggingUtils.logMessage(
                getClass().getName(),
                "Got %d -> %d filtered routes. Of those, %d are nonzero degree.",
                destRoutes.size(), destToShortest.size(),
                destToShortest.values()
                        .stream()
                        .map(TravelRoute::getRoute)
                        .filter(route -> route.size() > 2)
                        .count()
        );
        return destToShortest;
    }


}
