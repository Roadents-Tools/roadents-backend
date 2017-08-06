package com.reroute.backend.costs.providers.routing;

import com.google.common.collect.Lists;
import com.reroute.backend.costs.arguments.BulkCostArgs;
import com.reroute.backend.costs.arguments.CostArgs;
import com.reroute.backend.costs.interfaces.BulkCostProvider;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.StreamUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

        Map<String, DestinationLocation> destToString = b.stream()
                .collect(StreamUtils.collectWithKeys(DestinationLocation::toString));

        Map<String, Object> donutRoutingArgs = new HashMap<>();
        donutRoutingArgs.put("latitude", a.getCoordinates()[0]);
        donutRoutingArgs.put("longitude", a.getCoordinates()[1]);
        donutRoutingArgs.put("starttime", startTime.getUnixTime());
        for (int i = 0; i < b.size(); i++) {
            donutRoutingArgs.put("latitude" + (i + 2), b.get(i).getCoordinates()[0]);
            donutRoutingArgs.put("longitude" + (i + 2), b.get(i).getCoordinates()[1]);
            donutRoutingArgs.put("bestonly", false);
        }

        return ApplicationRunner.runApplication("DONUTAB", donutRoutingArgs).entrySet().stream()
                .filter(entry -> !entry.getKey().equals("ERROR"))
                .collect(StreamUtils.collectWithMapping(
                        entry -> destToString.get(entry.getKey()),
                        entry -> entry.getValue().stream()
                                .map(obj -> (TravelRoute) obj)
                                .collect(Collectors.toList())
                ));
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
        TimePoint startTime = new TimePoint(extractStartTime(randomSingleArgs), "America/Los Angeles");

        return buildRoutes(start, dests, startTime).entrySet().stream()
                .collect(StreamUtils.collectWithMapping(Map.Entry::getKey, Map.Entry::getValue));
    }
}
