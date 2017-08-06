package com.reroute.backend.logic.calculator;

import com.google.common.collect.Lists;
import com.reroute.backend.logic.generator.GeneratorSupport;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ilan on 12/24/16.
 */
public class CalculatorCore implements LogicCore {

    public static final String TAG = "MOLE";

    public static final String START_TIME_TAG = "starttime";
    public static final String LAT_TAG = "latitude";
    public static final String LONG_TAG = "longitude";
    public static final String LAT_2_TAG = "latitude2";
    public static final String LONG_2_TAG = "longitude2";
    public static final String TYPE_TAG = "type";
    public static final String TIME_DELTA_TAG = "timedelta";

    public static final String DEST_LIST_TAG = "DESTS";
    public static final String ROUTE_LIST_TAG = "ROUTES";

    private static Map<DestinationLocation, TravelRoute> runTowardsCore(TravelRoute baseroute, TimeDelta maxDelta, LocationType type) {
        return runTowardsCore(Lists.newArrayList(baseroute), maxDelta, type);
    }

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
                .map(TravelRoute::getTotalTime)
                .max(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(TimeDelta.NULL);

        StationRetriever.prepareWorld(base.getStart(), base.getStartTime(), maxDelta.plus(maxRouteTime));

        return baseRoutes.stream()
                .flatMap(buildDestRoutes(maxDelta, type))
                .collect(GeneratorSupport.OPTIMAL_ROUTES_FOR_DESTINATIONS);
    }

    private static Function<TravelRoute, Stream<TravelRoute>> buildDestRoutes(TimeDelta maxDelta, LocationType type) {
        return baseroute -> {

            TimeDelta[] deltas = CalculatorSupport.getTrueDeltasPerNode(baseroute, maxDelta);

            return IntStream.range(0, deltas.length).boxed().parallel()

                    //No extra time at that node, skip it
                    .filter(index -> deltas[index] != null && deltas[index].getDeltaLong() > 0)

                    //Get the dests surrounding each node
                    .flatMap(index -> CalculatorSupport.callGenForRouteAtIndex(index, baseroute, deltas[index], type)
                            .parallelStream());

        };
    }

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        //Get the args
        long startUnixTime = (long) args.get(START_TIME_TAG);
        TimePoint startTime = new TimePoint(startUnixTime, "America/Los_Angeles");
        double startLat = (double) args.get(LAT_TAG);
        double startLong = (double) args.get(LONG_TAG);
        double endLat = (double) args.get(LAT_2_TAG);
        double endLng = (double) args.get(LONG_2_TAG);
        long maxUnixTimeDelta = (long) args.get(TIME_DELTA_TAG);
        TimeDelta maxTimeDelta = new TimeDelta(maxUnixTimeDelta);
        LocationType type = new LocationType((String) args.get(TYPE_TAG), (String) args.get(TYPE_TAG));

        List<TravelRoute> baseRoutes = CalculatorSupport.buildRoute(
                new StartPoint(new double[] { startLat, startLong }),
                new StartPoint(new double[] { endLat, endLng }),
                startTime
        );
        //Run the core
        Map<DestinationLocation, TravelRoute> destsToRoutes = runTowardsCore(baseRoutes, maxTimeDelta, type);

        //Build the output
        Map<String, List<Object>> output = new HashMap<>();
        if (LoggingUtils.hasErrors()) {
            List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
            output.put("ERRORS", errs);
        }
        output.put(DEST_LIST_TAG, new ArrayList<>());
        output.put(ROUTE_LIST_TAG, new ArrayList<>());
        for (TravelRoute route : destsToRoutes.values()) {
            output.get(DEST_LIST_TAG).add(route.getDestination());
            output.get(ROUTE_LIST_TAG).add(route);
        }
        return output;
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
