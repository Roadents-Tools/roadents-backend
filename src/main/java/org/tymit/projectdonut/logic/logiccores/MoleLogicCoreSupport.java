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

    /**
     * Calculates the amount of time we can truly use to travel during each node in a route.
     *
     * @param route    the route to use
     * @param maxDelta the maximum amount of time we can arrive after route's original end time
     * @return an array of time deltas where each delta represents the available time at the node at that specific index
     * in the route
     */
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

                //The usable time = the added time from choosing the optimalNode over the curNode plus the amount of time
                //we are waiting anyway.
                rval[i] = optimalNode.getTotalTimeToArrive()
                        .minus(curNode.getTotalTimeToArrive())
                        .plus(optimalNode.getWaitTimeFromPrev());

                //We are going into the past of an alternate timeline!
                maxTime = route.getTimeAtNode(i - 1).plus(optimalNode.getTotalTimeToArrive());
            } else {

                // We need to be able to arrive in time to catch the bus, so we only can leave until then.
                // Technically not guranteed optimal, since there can be some hypothetical time between current route
                // and optimal route that maximizes wait time from i+1 to i+2, but for the sake of compute time this is
                // good enough.
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

    /**
     * Gets the transit time between 2 stations closest to a given maximum time.
     *
     * @param st1     the station to start at
     * @param st2     the station to end at
     * @param base    the time we begin at st1
     * @param maxTime the maximum time we are allowed to arrive at st2
     * @return a TravelRouteNode representing travelling from st1 to st2,
     * arriving at the latest possible time before maxTime
     */
    public static TravelRouteNode getLatestNodeConnecting(TransStation st1, TransStation st2, TimePoint base, TimePoint maxTime) {

        TimeDelta maxDelta = base.timeUntil(maxTime);

        //Create an easily searchable set of all chains containing st2
        Set<TransChain> st2Chains = StationRetriever.getStations(st2.getCoordinates(), 2, base, maxDelta, null, null)
                .stream()
                .map(TransStation::getChain)
                .distinct()
                .collect(Collectors.toSet());

        return StationRetriever.getStations(st1.getCoordinates(), 2, base, maxDelta, null, null).stream()
                .map(TransStation::getChain)
                .distinct()
                .filter(st2Chains::contains) //First get all chains containing both st1 and st2

                .map(chain1 -> {
                    TimePoint st1arrive = chain1.getSchedule(st1).getNextArrival(base);
                    TimePoint st2arrive = chain1.getSchedule(st2).getNextArrival(st1arrive);
                    return new TravelRouteNode.Builder()
                            .setPoint(st2)
                            .setWaitTime(base.timeUntil(st1arrive).getDeltaLong())
                            .setTravelTime(st1arrive.timeUntil(st2arrive).getDeltaLong())
                            .build();
                }) //Then we build nodes for these chains

                .filter(node -> base.plus(node.getTotalTimeToArrive()).timeUntil(maxTime).getDeltaLong() >= 0)
                .min(Comparator.comparing(node -> base.plus(node.getTotalTimeToArrive()).timeUntil(maxTime)))
                .orElse(null); // And finally compare
    }

    /**
     * Calls the Donut core for a route at a specific node in the route.
     *
     * @param index the index of the node in the route, with 0 being the starting node
     * @param route the route to retrieve the node from
     * @param delta the max time delta to use
     * @param type  the type to search for
     * @return a list of routes split from route at index ending in locations of type type
     */
    public static List<TravelRoute> callDonutForRouteAtIndex(int index, TravelRoute route, TimeDelta delta, LocationType type) {

        TravelRouteNode node = route.getRoute().get(index);

        Map<String, Object> donutParams = new ConcurrentHashMap<>();
        donutParams.put(DonutLogicCore.TYPE_TAG, type.getEncodedname());
        donutParams.put(DonutLogicCore.TIME_DELTA_TAG, delta.getDeltaLong());
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

    /**
     * Builds a route from a to b. Mainly a helper method in case other optimizations are necessary.
     *
     * @param a         the point to start at
     * @param b         the point to end at
     * @param startTime the time to start at
     * @return the route from a to b starting at time startTime
     */
    public static TravelRoute buildRoute(LocationPoint a, LocationPoint b, TimePoint startTime) {
        CostArgs arg = new CostArgs()
                .setCostTag("routes")
                .setSubject(b)
                .setArg("p1", a)
                .setArg("starttime", startTime);
        return (TravelRoute) CostCalculator.getCostValue(arg);
    }


}
