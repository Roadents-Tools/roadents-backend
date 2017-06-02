package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.model.routing.TravelRouteNode;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 12/24/16.
 */
public class MoleLogicCoreSupport {

    public static TransStation getStationWithSchedule(TransStation station) {
        return DonutLogicSupport.getStationWithSchedule(station);
    }

    public static TimeDelta[] getTrueDeltasPerNode(TravelRoute route, TimeDelta maxDelta) {

        final int routeLen = route.getRoute().size();
        if (routeLen == 2) return new TimeDelta[] { maxDelta, maxDelta }; //On a basic route, we go from a to b.

        TimeDelta[] rval = new TimeDelta[routeLen];
        rval[routeLen - 1] = maxDelta; //We allow maxDelta time from the destination

        TimePoint maxTime = route.getTimeAtNode(routeLen - 1).plus(maxDelta);
        TravelRouteNode[] optimalCache = new TravelRouteNode[routeLen];

        for (int i = routeLen - 2; i >= 0; i--) {
            TravelRouteNode curNode = route.getRoute().get(i);
            if (curNode.arrivesByTransportation()) {
                TransStation curStation = (TransStation) curNode.getPt();
                TransStation prevStation = (TransStation) route.getRoute().get(i - 1).getPt();
                TravelRouteNode optimalNode = getLatestNodeConnecting(prevStation, curStation, route.getTimeAtNode(i - 1), maxTime);
                optimalCache[i] = optimalNode;
                rval[i] = optimalNode.getTotalTimeToArrive()
                        .minus(curNode.getTotalTimeToArrive())
                        .plus(optimalNode.getWaitTimeFromPrev());
                maxTime = route.getTimeAtNode(i - 1).plus(optimalNode.getTotalTimeToArrive());
            } else {
                long maxWait = Math.max(
                        optimalCache[i + 1].getWaitTimeFromPrev().getDeltaLong(),
                        route.getRoute().get(i + 1).getWaitTimeFromPrev().getDeltaLong()
                );
                rval[i] = new TimeDelta(maxWait);
                maxTime = maxTime.minus(curNode.getWalkTimeFromPrev());
            }
        }

        return rval;
    }

    public static TravelRouteNode getLatestNodeConnecting(TransStation st1, TransStation st2, TimePoint base, TimePoint maxTime) {

        TimeDelta maxDelta = base.timeUntil(maxTime);

        Set<TransChain> st2Chains = StationRetriever.getStations(st2.getCoordinates(), 2, base, maxDelta, null, null)
                .stream()
                .map(TransStation::getChain)
                .distinct()
                .collect(Collectors.toSet());

        return StationRetriever.getStations(st1.getCoordinates(), 2, base, maxDelta, null, null).stream()
                .map(TransStation::getChain)
                .distinct()
                .filter(st2Chains::contains)
                .map(chain1 -> {
                    TimePoint st1arrive = chain1.getSchedule(st1).getNextArrival(base);
                    TimePoint st2arrive = chain1.getSchedule(st2).getNextArrival(st1arrive);
                    return new TravelRouteNode.Builder()
                            .setPoint(st2)
                            .setWaitTime(base.timeUntil(st1arrive).getDeltaLong())
                            .setTravelTime(st1arrive.timeUntil(st2arrive).getDeltaLong())
                            .build();
                })
                .min(Comparator.comparing(node -> base.plus(node.getTotalTimeToArrive()).timeUntil(maxTime)))
                .orElse(null);
    }

    public static List<TravelRoute> callDonutForRouteAtIndex(int index, TravelRoute route, TimeDelta[] deltas, LocationType type) {

        TravelRouteNode node = route.getRoute().get(index);

        Map<String, Object> donutParams = new ConcurrentHashMap<>();
        donutParams.put(DonutLogicCore.TYPE_TAG, type.getEncodedname());
        donutParams.put(DonutLogicCore.TIME_DELTA_TAG, deltas[index].getDeltaLong());
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

    public static TravelRoute buildRoute(LocationPoint a, LocationPoint b, TimePoint startTime) {
        CostArgs arg = new CostArgs()
                .setCostTag("routes")
                .setSubject(b)
                .setArg("p1", a)
                .setArg("starttime", startTime);
        return (TravelRoute) CostCalculator.getCostValue(arg);
    }


}
