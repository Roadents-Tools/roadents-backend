package com.reroute.backend.logic.finder;

import com.google.common.collect.Lists;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.StreamUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Created by ilan on 4/30/17.
 */
public class FinderCore implements LogicCore {

    public static final String TAG = "WEASEL";
    /*private static final String TIME_COST_START_TIME_TAG = "starttime";
    private static final String TIME_COST_COMPARISON_TAG = "comparison";
    private static final String TIME_COST_COMPARE_VALUE_TAG = "compareto";
    private static final String TIME_COST_POINT_ONE_TAG = "p1";
    private final static String TIME_COST_TAG = "time";*/

    @Override
    public ApplicationResult performLogic(ApplicationRequest args) {

        List<StartPoint> locs = args.getStarts();

        TimeDelta maxDelta = args.getMaxDelta();

        Predicate<TravelRoute> isInAllRanges = locs.stream()
                .map(loc -> LogicUtils.isRouteInRange(loc, maxDelta))
                .reduce(Predicate::or)
                .orElse(rt -> false);

        ApplicationRequest donutArgs = new ApplicationRequest.Builder("DONUT")
                .withStartPoint(args.getStarts().get(0))
                .withStartTime(args.getStartTime())
                .withMaxDelta(maxDelta)
                .withQuery(args.getQuery())
                .withFilter(isInAllRanges)
                .build();

        ApplicationResult donutResults = ApplicationRunner.runApplication(donutArgs);

        Map<DestinationLocation, TravelRoute> routeMap = donutResults.getResult().stream()
                .collect(StreamUtils.collectWithKeys(TravelRoute::getDestination));

        /*
        TODO: De-costify
        Set<BulkCostArgs> ags = locs.stream()
                .map(pt -> new BulkCostArgs()
                        .setCostTag(TIME_COST_TAG)
                        .setArg(TIME_COST_START_TIME_TAG, args.getStartTime())
                        .setArg(TIME_COST_COMPARE_VALUE_TAG, maxDelta)
                        .setArg(TIME_COST_COMPARISON_TAG, "<=")
                        .setArg(TIME_COST_POINT_ONE_TAG, pt))
                .peek(bkarg -> routeMap.keySet().forEach(bkarg::addSubject))
                .collect(Collectors.toSet());

        for (BulkCostArgs ag : ags) {
            Map<Object, Boolean> filters = CostCalculator.isWithinCosts(ag);
            filters.keySet().stream()
                    .filter(dest -> !filters.getOrDefault(dest, false))
                    .forEach(routeMap::remove);
        }
        */

        return ApplicationResult.ret(Lists.newArrayList(routeMap.values()));

    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton(TAG);
    }

    @Override
    public boolean isValid(ApplicationRequest request) {
        return request.getStarts() != null && !request.getStarts().isEmpty() && request.getStarts().size() > 1
                && request.getStartTime() != null && !TimePoint.NULL.equals(request.getStartTime())
                && request.getMaxDelta() != null && !TimeDelta.NULL.equals(request.getMaxDelta())
                && request.getQuery() != null;
    }


}
