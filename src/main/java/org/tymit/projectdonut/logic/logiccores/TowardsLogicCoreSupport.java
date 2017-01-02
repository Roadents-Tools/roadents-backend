package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 12/24/16.
 */
public class TowardsLogicCoreSupport {

    public static TransStation getStationWithSchedule(TransStation station) {
        return DonutLogicSupport.getStationWithSchedule(station);
    }

    public static TimeModel[] getTrueDeltasPerNode(TravelRoute route, TimeModel maxDelta) {
        final int routeLen = route.getRoute().size();
        TimeModel[] rval = new TimeModel[routeLen];
        rval[routeLen - 1] = maxDelta.clone();

        // TODO: Figure out and implement calculating each node's truly available time
        // Research notes:
        // Each node has at least the wait time from the node in front of it.
        // The end node (n) has maxDelta time.
        //
        // Assume a walk time w to the end node. This should remain constant for any time of day.
        // At node n-1 we are allowed to arrive maxDelta after the original arrive time. Therefore
        // we are allowed to stop at most maxDelta after the original stop. We store this stop time
        // asced with the station as the new maximum valid stop time.
        //
        // For station i we check the last valid stop for station i+1 and then keep going through i's
        // schedule until we go over i+1's valid time. The difference between the old stop time and the arrive
        // time from i-1 is our valid delta.

        return rval;
    }

    public static List<TravelRoute> callDonutForRouteAtIndex(int index, TravelRoute route, TimeModel[] deltas, LocationType type) {

        TravelRouteNode node = route.getRoute().get(index);

        Map<String, Object> donutParams = new ConcurrentHashMap<>();
        donutParams.put(DonutLogicCore.TYPE_TAG, type.getEncodedname());
        donutParams.put(DonutLogicCore.TIME_DELTA_TAG, deltas[index].getUnixTimeDelta());
        donutParams.put(DonutLogicCore.LAT_TAG, node.getPt().getCoordinates()[0]);
        donutParams.put(DonutLogicCore.LONG_TAG, node.getPt().getCoordinates()[1]);
        donutParams.put(DonutLogicCore.START_TIME_TAG, route.getTimeAtNode(index));

        Map<String, List<Object>> rval = ApplicationRunner.runApplication(DonutLogicCore.TAG, donutParams);
        if (rval.containsKey("ERRORS")) {
            rval.get("ERRORS").forEach(err -> LoggingUtils.logError((Exception) err));
        }
        List<Object> routeList = rval.getOrDefault("ROUTES", Collections.emptyList());

        return routeList.parallelStream()
                .map(obj -> (TravelRoute) obj)
                .map(destRoute -> {
                    TravelRoute base = route.cloneAtNode(index);
                    destRoute.getRoute().stream().skip(1).forEach(base::addNode);
                    return base;
                })
                .collect(Collectors.toList());
    }
}
