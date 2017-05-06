package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.costs.BulkCostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TravelRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 4/30/17.
 */
public class WeaselLogicCore implements LogicCore {

    public static final String LAT_TAG = "latitude";
    public static final String LONG_TAG = "longitude";
    public static final String TIME_DELTA_TAG = "timedelta";
    public static final String DEST_LIST_TAG = "DESTS";
    public static final String START_TIME_TAG = "starttime";
    public static final String ROUTE_LIST_TAG = "ROUTES";
    public static final String TAG = "WEASEL";
    private static final String TIME_COST_START_TIME_TAG = "starttime";
    private static final String TIME_COST_COMPARISON_TAG = "comparison";
    private static final String TIME_COST_COMPARE_VALUE_TAG = "compareto";
    private static final String TIME_COST_POINT_ONE_TAG = "p1";
    private final static String TIME_COST_TAG = "time";

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        List<StartPoint> locs = new ArrayList<>();
        for (int i = 0; args.containsKey(LAT_TAG + i); i++) {
            double lat = (double) args.get(LAT_TAG + i);
            if (!args.containsKey(LONG_TAG + i)) {
                break;
            }
            double lng = (double) args.get(LONG_TAG + i);
            locs.add(new StartPoint(new double[] { lat, lng }));
        }

        Map<String, List<Object>> donutResults = LogicCoreHelper.getHelper().runCore("DONUT", args);

        List<Object> unfilteredRouteObjList = donutResults.get(ROUTE_LIST_TAG);

        Map<DestinationLocation, TravelRoute> routeMap = unfilteredRouteObjList.stream()
                .map(obj -> (TravelRoute) obj)
                .collect(ConcurrentHashMap::new, (map, rt) -> map.put(rt.getDestination(), rt), ConcurrentHashMap::putAll);

        Set<BulkCostArgs> ags = locs.stream()
                .map(pt -> new BulkCostArgs()
                        .setCostTag(TIME_COST_TAG)
                        .setArg(TIME_COST_START_TIME_TAG, args.get(START_TIME_TAG))
                        .setArg(TIME_COST_COMPARE_VALUE_TAG, args.get(TIME_DELTA_TAG))
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


        Map<String, List<Object>> rval = new ConcurrentHashMap<>();
        rval.put(ROUTE_LIST_TAG, new ArrayList<>(routeMap.values()));
        rval.put(DEST_LIST_TAG, new ArrayList<>(routeMap.keySet()));
        return rval;
    }

    @Override
    public String getTag() {
        return TAG;
    }


}
