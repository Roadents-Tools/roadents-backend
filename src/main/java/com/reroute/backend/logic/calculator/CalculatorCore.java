package com.reroute.backend.logic.calculator;

import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * Created by ilan on 12/24/16.
 */
public class CalculatorCore implements LogicCore {

    public static final String TAG = "MOLE";


    //V1 Algorithm
    private static Map<DestinationLocation, TravelRoute> runTowardsCore(List<TravelRoute> baseRoutes, TimeDelta maxDelta, LocationType type) {
        if (baseRoutes == null || maxDelta == null || type == null || baseRoutes.isEmpty() || TimeDelta.NULL.equals(maxDelta)) {
            return Collections.emptyMap();
        }

        TravelRoute base = baseRoutes.get(0);

        Predicate<TravelRoute> routesSame = rt -> rt.getStart().equals(base.getStart())
                && rt.getStartTime().equals(base.getStartTime())
                && rt.getCurrentEnd().equals(base.getCurrentEnd());

        if (!baseRoutes.stream().allMatch(routesSame)) {
            LoggingUtils.logError(TAG, "Not all seed routes represent the same AB path.");
            return Collections.emptyMap();
        }


        TimeDelta maxRouteTime = baseRoutes.stream()
                .map(TravelRoute::getTime)
                .max(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(TimeDelta.NULL);

        WorldInfo request = new WorldInfo.Builder()
                .setCenter(base.getStart())
                .setStartTime(base.getStartTime())
                .setMaxDelta(maxDelta.plus(maxRouteTime))
                .build();
        StationRetriever.prepareWorld(request);

        return baseRoutes.stream()
                .limit(3)
                .flatMap(buildDestRoutes(maxDelta, type))
                .collect(LogicUtils.OPTIMAL_ROUTES_FOR_DESTINATIONS);
    }

    private static Function<TravelRoute, Stream<TravelRoute>> buildDestRoutes(TimeDelta maxDelta, LocationType type) {
        return baseroute -> {
            TimeDelta[] deltas = CalculatorSupport.getTrueDeltasPerNode(baseroute, maxDelta);
            return IntStream.range(0, deltas.length).boxed().parallel()
                    .filter(index -> deltas[index] != null && deltas[index].getDeltaLong() > 0)
                    .flatMap(index -> CalculatorSupport.callGenForRouteAtIndex(index, baseroute, deltas[index], type)
                            .parallelStream());

        };
    }

    //V2 Algorithm
    private static Map<DestinationLocation, TravelRoute> runTowardsCoreV2(List<TravelRoute> baseRoutes, TimeDelta maxDelta, LocationType type) {
        if (baseRoutes == null || maxDelta == null || type == null || baseRoutes.isEmpty() || TimeDelta.NULL.equals(maxDelta)) {
            return Collections.emptyMap();
        }
        TravelRoute base = baseRoutes.get(0);
        Predicate<TravelRoute> routesSame = rt -> rt.getStart().equals(base.getStart())
                && rt.getStartTime().equals(base.getStartTime())
                && rt.getCurrentEnd().equals(base.getCurrentEnd());

        if (!baseRoutes.stream().allMatch(routesSame)) {
            LoggingUtils.logError(TAG, "Not all seed routes represent the same AB path.");
            return Collections.emptyMap();
        }

        TimeDelta abDelta = baseRoutes.stream()
                .map(TravelRoute::getTime)
                .min(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(TimeDelta.NULL);
        if (TimeDelta.NULL.equals(abDelta)) {
            LoggingUtils.logError(TAG, "Could not get AB time.");
            return Collections.emptyMap();
        }

        TimePoint startTime = base.getStartTime();
        StartPoint a = base.getStart();
        DestinationLocation b = base.getDestination();
        TimeDelta donutMaxDelta = abDelta.plus(maxDelta);

        ApplicationRequest donutRequest = new ApplicationRequest.Builder("DONUT")
                .withStartPoint(a)
                .withStartTime(startTime)
                .withMaxDelta(donutMaxDelta)
                .withQuery(type)
                .withFilter(LogicUtils.isRouteInRange(b, donutMaxDelta))
                .build();

        ApplicationResult result = ApplicationRunner.runApplication(donutRequest);

        Map<DestinationLocation, List<TravelRoute>> allRoutes = result.getResult().stream()
                .collect(StreamUtils.collectWithMapping(
                        TravelRoute::getDestination,
                        rt -> runDestToB(rt, b, donutMaxDelta)
                ));

        Map<DestinationLocation, TravelRoute> minRoutes = new ConcurrentHashMap<>();
        allRoutes.forEach((key, value) -> {
            TravelRoute minRoute = value.stream().min(Comparator.comparing(TravelRoute::getTime)).get();
            minRoutes.put(key, minRoute);
        });

        return minRoutes;
    }

    private static List<TravelRoute> runDestToB(TravelRoute destRoute, DestinationLocation b, TimeDelta maxDelta) {
        ApplicationRequest routeRequest = new ApplicationRequest.Builder("DONUTAB_MULTI")
                .withStartPoint(new StartPoint(destRoute.getCurrentEnd().getCoordinates()))
                .withStartTime(destRoute.getEndTime())
                .withEndPoint(b)
                .withFilter(LogicUtils.isRouteInRange(b, maxDelta.minus(destRoute.getTime())))
                .build();

        return ApplicationRunner.runApplication(routeRequest).getResult().stream()
                .map(rt -> {
                    TravelRoute base = destRoute.copy();
                    rt.getRoute().stream().skip(1).forEach(base::addNode);
                    return base;
                })
                .collect(Collectors.toList());
    }



    @Override
    public ApplicationResult performLogic(ApplicationRequest request) {

        //Get the args
        if (!TAG.equals(request.getTag())) {
            throw new IllegalArgumentException(
                    "Request passed to invalid core. Request tag: " + request.getTag() + ". Core tag: " + TAG
            );
        }

        TimePoint startTime = request.getStartTime();
        StartPoint start = request.getStarts().get(0);
        DestinationLocation end = request.getEnds().get(0);
        TimeDelta maxTimeDelta = request.getMaxDelta();
        LocationType type = request.getQuery();

        List<TravelRoute> baseRoutes = CalculatorSupport.buildRoute(
                start,
                end,
                startTime
        );
        //Run the core
        Map<DestinationLocation, TravelRoute> destsToRoutes = runTowardsCore(baseRoutes, maxTimeDelta, type);

        //Build the output
        return ApplicationResult.ret(new ArrayList<>(destsToRoutes.values())).withErrors(LoggingUtils.getErrors());
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton(TAG);
    }

    public boolean isValid(ApplicationRequest request) {
        return request.getStarts() != null && !request.getStarts().isEmpty() && request.getStarts().size() <= 1
                && request.getEnds() != null && !request.getEnds().isEmpty() && request.getEnds().size() <= 1
                && request.getStartTime() != null && !TimePoint.NULL.equals(request.getStartTime())
                && request.getMaxDelta() != null && !TimeDelta.NULL.equals(request.getMaxDelta())
                && request.getQuery() != null;
    }
}
