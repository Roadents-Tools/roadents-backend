package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TravelRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by ilan on 4/30/17.
 */
public class WeaselLogicCore implements LogicCore {

    public static final String TIME_COST_START_TIME_TAG = "starttime";
    public static final String TIME_COST_COMPARISON_TAG = "comparison";
    public static final String TIME_COST_COMPARE_VALUE_TAG = "compareto";
    public static final String TIME_COST_POINT_TWO_TAG = "p2";
    public static final String LAT_TAG = "latitude";
    public static final String LONG_TAG = "longitude";
    public static final String TIME_DELTA_TAG = "timedelta";
    public static final String DEST_LIST_TAG = "DESTS";
    public static final String ROUTE_LIST_TAG = "ROUTES";
    public static final String TAG = "WEASEL";
    private static String TIME_COST_TAG = "time";

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        List<LocationPoint> locs = new ArrayList<>();
        for (int i = 0; args.containsKey(LAT_TAG + i); i++) {
            double lat = (double) args.get(LAT_TAG + i);
            if (!args.containsKey(LONG_TAG + i)) {
                break;
            }
            double lng = (double) args.get(LONG_TAG + i);
            locs.add(new StartPoint(new double[] { lat, lng }));
        }

        TimeDelta maxDelta = new TimeDelta((Long) args.getOrDefault(TIME_DELTA_TAG, -1));

        Predicate<TravelRoute> timeChecker = rt -> locs.parallelStream()
                .map(pt -> new CostArgs()
                        .setCostTag(TIME_COST_TAG)
                        .setSubject(rt.getDestination())
                        .setArg(TIME_COST_START_TIME_TAG, rt.getStartTime())
                        .setArg(TIME_COST_POINT_TWO_TAG, pt)
                        .setArg(TIME_COST_COMPARE_VALUE_TAG, maxDelta)
                        .setArg(TIME_COST_COMPARISON_TAG, "<=")
                )
                .allMatch(CostCalculator::isWithinCosts);

        Map<String, List<Object>> donutResults = LogicCoreHelper.getHelper().runCore("DONUT", args);

        List<Object> dests = new ArrayList<>();
        List<Object> routes = donutResults.get(ROUTE_LIST_TAG)
                .parallelStream()
                .map(rt -> (TravelRoute) rt)
                .filter(timeChecker)
                .peek(rt -> dests.add(rt.getDestination()))
                .collect(Collectors.toList());

        Map<String, List<Object>> rval = new ConcurrentHashMap<>();
        rval.put(ROUTE_LIST_TAG, routes);
        rval.put(DEST_LIST_TAG, dests);
        return rval;
    }

    @Override
    public String getTag() {
        return TAG;
    }


}
