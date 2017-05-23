package org.tymit.projectdonut.costs.providers.routing;

import com.google.common.collect.Lists;
import org.tymit.projectdonut.costs.BulkCostArgs;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.providers.BulkCostProvider;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TravelRoute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 5/23/17.
 */
public class DonutRouteCostProvider extends RouteCostProvider implements BulkCostProvider {

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    protected TravelRoute buildRoute(StartPoint a, DestinationLocation b, TimePoint start) {
        return buildRoutes(a, Lists.newArrayList(b), start).get(0);
    }

    protected List<TravelRoute> buildRoutes(StartPoint a, List<DestinationLocation> b, TimePoint startTime) {

        Map<String, Object> donutRoutingArgs = new HashMap<>();
        donutRoutingArgs.put("latitude", a.getCoordinates()[0]);
        donutRoutingArgs.put("longitude", a.getCoordinates()[1]);
        donutRoutingArgs.put("starttime", startTime.getUnixTime());
        for (int i = 0; i < b.size(); i++) {
            donutRoutingArgs.put("latitude" + i, b.get(i).getCoordinates()[0]);
            donutRoutingArgs.put("longitude" + i, b.get(i).getCoordinates()[1]);
        }

        Map<String, List<Object>> callVal = ApplicationRunner.runApplication("DONUTAB", donutRoutingArgs);
        return callVal.get("ROUTES").stream().map(rt -> (TravelRoute) rt).collect(Collectors.toList());
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
        TimePoint startTime = new TimePoint(extractStartTime(randomSingleArgs), "America/Los Angeles");

        List<TravelRoute> routes = buildRoutes(start, dests, startTime);

        Map<Object, Object> rval = new ConcurrentHashMap<>();
        for (int i = 0; i < destSize; i++) {
            rval.put(keys.get(i), routes.get(i));
        }
        return rval;
    }
}
