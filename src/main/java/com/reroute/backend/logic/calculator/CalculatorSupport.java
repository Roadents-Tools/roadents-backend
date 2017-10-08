package com.reroute.backend.logic.calculator;

import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.logic.generator.GeneratorCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ilan on 12/24/16.
 */
public class CalculatorSupport {

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

        TimePoint maxTime = route.getTimePointAt(routeLen - 1).plus(maxDelta);
        TravelRouteNode[] optimalCache = new TravelRouteNode[routeLen];

        for (int i = routeLen - 2; i >= 0; i--) {
            TravelRouteNode curNode = route.getRoute().get(i);
            if (curNode.arrivesByTransportation()) {
                TransStation curStation = (TransStation) curNode.getPt();
                TransStation prevStation = (TransStation) route.getRoute().get(i - 1).getPt();
                TravelRouteNode optimalNode = getLatestNodeConnecting(prevStation, curStation, route.getTimePointAt(i - 1), maxTime);
                optimalCache[i] = optimalNode;

                //The usable time = the added time from choosing the optimalNode over the curNode plus the amount of time
                //we are waiting anyway.
                rval[i] = optimalNode.getTotalTimeToArrive()
                        .minus(curNode.getTotalTimeToArrive())
                        .plus(optimalNode.getWaitTimeFromPrev());

                //We are going into the past of an alternate timeline!
                maxTime = route.getTimePointAt(i - 1).plus(optimalNode.getTotalTimeToArrive());
            } else {

                // We need to be able to arrive in time to catch the bus, so we only can leave until then.
                // Technically not guranteed optimal, since there can be some hypothetical time between current route
                // and optimal route that maximizes wait time from i+1 to i+2, but for the sake of compute time this is
                // good enough.
                long maxWait = Math.max(
                        optimalCache[i + 1] != null ? optimalCache[i + 1].getWaitTimeFromPrev().getDeltaLong() : 0,
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
        Map<TransChain, List<SchedulePoint>> st2Chains = StationRetriever.getChainsForStation(st2, null);

        return StationRetriever.getChainsForStation(st1, null)
                .entrySet()
                .stream()
                .distinct()
                .filter(entry -> st2Chains.containsKey(entry.getKey())) //First get all chains containing both st1 and st2

                .map(entry -> {
                    TimePoint st1arrive = st1.withSchedule(entry.getKey(), entry.getValue()).getNextArrival(base);
                    TimePoint st2arrive = st2.withSchedule(entry.getKey(), st2Chains.get(entry.getKey()))
                            .getNextArrival(st1arrive);
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
     * Calls the Generator core for a route at a specific node in the route.
     *
     * @param index the index of the node in the route, with 0 being the starting node
     * @param route the route to retrieve the node from
     * @param delta the max time delta to use
     * @param type  the type to search for
     * @return a list of routes split from route at index ending in locations of type type
     */
    public static List<TravelRoute> callGenForRouteAtIndex(int index, TravelRoute route, TimeDelta delta, LocationType type) {

        TravelRouteNode node = route.getRoute().get(index);

        ApplicationRequest request = new ApplicationRequest.Builder(GeneratorCore.TAG)
                .withQuery(type)
                .withStartPoint(new StartPoint(node.getPt().getCoordinates()))
                .withStartTime(route.getTimePointAt(index))
                .withMaxDelta(delta)
                .build();

        ApplicationResult rval = ApplicationRunner.runApplication(request);
        if (rval.hasErrors()) {
            rval.getErrors().forEach(LoggingUtils::logError);
        }

        return rval.getResult().stream()
                .map(destRoute -> {
                    TravelRoute base = route.copyAt(index);
                    destRoute.getRoute().stream().skip(1).forEach(base::addNode);
                    return base;
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds seed routes from A to B.
     *
     * @param a         the point to start at
     * @param b         the point to end at
     * @param startTime the time to start at
     * @return the list of routes from a to b starting at time startTime
     */
    public static List<TravelRoute> buildRoute(StartPoint a, DestinationLocation b, TimePoint startTime) {
        TimeDelta walkTime = LocationUtils.timeBetween(a, b);

        ApplicationRequest pathmakerRequest = new ApplicationRequest.Builder("DONUTAB_BEST")
                .withStartPoint(a)
                .withEndPoint(b)
                .withStartTime(startTime)
                .withFilter(LogicUtils.isRouteInRange(b, walkTime))
                .withMaxDelta(new TimeDelta(20 * 60 * 1000))
                .build();

        ApplicationResult pathResult = ApplicationRunner.runApplication(pathmakerRequest);

        return pathResult.getResult();
    }


}
