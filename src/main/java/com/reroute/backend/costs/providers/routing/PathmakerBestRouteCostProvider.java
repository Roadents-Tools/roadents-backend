package com.reroute.backend.costs.providers.routing;

import com.google.common.collect.Lists;
import com.reroute.backend.costs.arguments.BulkCostArgs;
import com.reroute.backend.costs.arguments.CostArgs;
import com.reroute.backend.costs.interfaces.BulkCostProvider;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimePoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 5/23/17.
 */
public class PathmakerBestRouteCostProvider extends BestRouteCostProvider implements BulkCostProvider {

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    protected TravelRoute buildRoute(StartPoint a, DestinationLocation b, TimePoint start) {
        return buildRoutes(a, Lists.newArrayList(b), start).get(0);
    }

    protected List<TravelRoute> buildRoutes(StartPoint a, List<DestinationLocation> b, TimePoint startTime) {

        ApplicationRequest bestRouteRequest = new ApplicationRequest.Builder("DONUTAB_BEST")
                .withStartPoint(a)
                .withEndPoints(b)
                .withStartTime(startTime)
                .build();

        ApplicationResult callVal = ApplicationRunner.runApplication(bestRouteRequest);

        return callVal.getResult();
    }

    @Override
    public Map<Object, Boolean> isWithinCosts(BulkCostArgs args) {
        //TODO
        return null;
    }

    @Override
    public Map<Object, Object> getCostValue(BulkCostArgs args) {

        Collection<CostArgs> unorderedArgs = args.splitSingular().values();
        List<Object> keys = new ArrayList<>();
        List<DestinationLocation> dests = new ArrayList<>();
        CostArgs randomSingleArgs = null;

        for (CostArgs singArg : unorderedArgs) {
            if (randomSingleArgs == null) randomSingleArgs = singArg;
            keys.add(singArg.getSubject());
            dests.add(extractEnd(singArg));
        }

        int destSize = dests.size();
        StartPoint start = extractStart(randomSingleArgs);
        TimePoint startTime = TimePoint.from(extractStartTime(randomSingleArgs), "America/Los Angeles");

        List<TravelRoute> routes = buildRoutes(start, dests, startTime);

        Map<Object, Object> rval = new ConcurrentHashMap<>();
        for (int i = 0; i < destSize; i++) {
            rval.put(keys.get(i), routes.get(i));
        }
        return rval;
    }
}
