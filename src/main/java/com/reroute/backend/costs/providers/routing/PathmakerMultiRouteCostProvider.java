package com.reroute.backend.costs.providers.routing;

import com.google.common.collect.Lists;
import com.reroute.backend.costs.arguments.BulkCostArgs;
import com.reroute.backend.costs.arguments.CostArgs;
import com.reroute.backend.costs.interfaces.BulkCostProvider;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.StreamUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ilan on 5/23/17.
 */
public class PathmakerMultiRouteCostProvider extends MultiRouteCostProvider implements BulkCostProvider {

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    protected List<TravelRoute> buildRoute(StartPoint a, DestinationLocation b, TimePoint start) {
        return buildRoutes(a, Lists.newArrayList(b), start).get(b);
    }

    protected Map<DestinationLocation, List<TravelRoute>> buildRoutes(StartPoint a, List<DestinationLocation> b, TimePoint startTime) {

        ApplicationRequest request = new ApplicationRequest.Builder("DONUTAB_MULTI")
                .withStartPoint(a)
                .withEndPoints(b)
                .withStartTime(startTime)
                .build();

        List<TravelRoute> fullResSet = ApplicationRunner.runApplication(request).getResult();

        return fullResSet.stream().collect(Collectors.groupingBy(TravelRoute::getDestination));

    }

    @Override
    public Map<Object, Boolean> isWithinCosts(BulkCostArgs args) {
        //TODO
        return null;
    }

    @Override
    public Map<Object, Object> getCostValue(BulkCostArgs args) {

        Collection<CostArgs> unorderedArgs = args.splitSingular().values();
        List<DestinationLocation> dests = new ArrayList<>();
        CostArgs randomSingleArgs = null;

        for (CostArgs singArg : unorderedArgs) {
            if (randomSingleArgs == null) randomSingleArgs = singArg;
            dests.add(extractEnd(singArg));
        }

        StartPoint start = extractStart(randomSingleArgs);
        TimePoint startTime = TimePoint.from(extractStartTime(randomSingleArgs), "America/Los Angeles");

        return buildRoutes(start, dests, startTime).entrySet().stream()
                .collect(StreamUtils.collectWithMapping(Map.Entry::getKey, Map.Entry::getValue));
    }
}
