package com.reroute.displayers.testdisplay.maproutedrawer;

import com.google.common.collect.Lists;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 7/16/17.
 */
public class RouteMapsPageGenerator {

    private static final String[] COLORS = new String[] {
            "0000ffff",
            "00ff00ff"
    };
    private static String API_KEY = "AIzaSyB0pBdXuC4VRte73qnVtE5pLmxNs3ju0Gg";


    public static Stream<String> generateIndividualPagesFromFile(String fileName) {
        return readRoutesFromFile(fileName).stream()
                .peek(a -> LoggingUtils.logMessage("MapGenerator", "Now using route %s.", a.toString()))
                .map(RouteMapsPageGenerator::generateIndivitualPage);
    }

    public static List<TravelRoute> readRoutesFromFile(String fileName) {
        try {
            String rawJson = Files.lines(Paths.get(fileName)).reduce((s, s2) -> s + s2).orElse("[]");
            List<TravelRoute> rval = new ArrayList<>();
            new TravelRouteJsonConverter().fromJson(rawJson, rval);
            return rval;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyList();
        }
    }

    public static String generateCompositePageFromFile(String fileName) {
        return generatePage(readRoutesFromFile(fileName));
    }

    private static String generatePage(List<TravelRoute> routes) {

        TimePoint startTime = TimePoint.NULL;
        TimePoint endTime = TimePoint.NULL;


        List<LocationPoint> locations = new ArrayList<>();
        for (TravelRoute route : routes) {
            if (startTime == TimePoint.NULL || route.getStartTime().getUnixTime() < startTime.getUnixTime()) {
                startTime = route.getStartTime();
            }
            if (endTime == TimePoint.NULL || route.getEndTime().getUnixTime() < endTime.getUnixTime()) {
                endTime = route.getEndTime();
            }

            route.getRoute().stream()
                    .map(TravelRouteNode::getPt)
                    .forEach(locations::add);
        }

        LocationPoint center = new StartPoint(locations.stream()
                .map(LocationPoint::getCoordinates)
                .map(crds -> new double[] { crds[0] / locations.size(), crds[1] / locations.size() })
                .reduce(new double[] { 0, 0 }, (da, db) -> new double[] { da[0] + db[0], da[1] + db[1] })
        );

        //StationRetriever.prepareWorld(center, startTime, startTime.timeUntil(endTime));

        List<String> colorMap = Arrays.asList(COLORS);

        List<String> paths = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            String color = colorMap.get(i % colorMap.size());
            TravelRoute area = routes.get(i);

            paths.add(mapRouteToPath(getMappableRoute(area), color));
        }

        String circleJoiner = paths.stream()
                .collect(StreamUtils.joinToString(",\n"));

        String javascript = String.format(RouteMapsPageConstants.JS_FORMAT, circleJoiner);

        return String.format(RouteMapsPageConstants.HTML_FORMAT, javascript, API_KEY);
    }

    private static TravelRoute getMappableRoute(TravelRoute base) {
        TravelRoute nRoute = new TravelRoute(base.getStart(), base.getStartTime());

        for (int i = 0; i < base.getRoute().size() - 1; i++) {
            TravelRouteNode cur = base.getRoute().get(i);
            TravelRouteNode next = base.getRoute().get(i + 1);
            getNodesBetween(base, cur, next).forEach(nRoute::addNode);
        }

        return nRoute;
    }

    private static Stream<TravelRouteNode> getNodesBetween(TravelRoute rt, TravelRouteNode a, TravelRouteNode b) {
        if (!b.arrivesByTransportation() || b.getPt() != null) {
            return Stream.of(b);
        }

        TransStation aStat = (TransStation) a.getPt();
        TimePoint aTime = rt.getTimeAtNode(a);

        Map<TransChain, List<SchedulePoint>> achains = StationRetriever.getChainsForStation(aStat, null);
        if (achains.isEmpty()) {
            LoggingUtils.logError("RouteMapGen", "Got no achains.");
            throw new RuntimeException();
        }

        Map<TransChain, TransStation> chainToASched = achains.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(Map.Entry::getKey, entry -> aStat.withSchedule(entry.getKey(), entry
                        .getValue())));

        Set<TransChain> validAs = chainToASched.entrySet().stream()
                .filter(entry -> {
                    TimeDelta expected = b.getWaitTimeFromPrev();
                    TimeDelta actual = aTime.timeUntil(entry.getValue().getNextArrival(aTime));
                    long diff = Math.abs(expected.getDeltaLong() - actual.getDeltaLong());
                    if (diff > 1000) {
                        LoggingUtils.logMessage("RouteMapGen", "Filtering diff: %f", diff / 1000.);
                        return false;
                    }
                    return true;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (validAs.isEmpty()) {
            LoggingUtils.logError("RouteMapGen", "Got no validas.");
            throw new RuntimeException();
        }

        TimeDelta fuzz = new TimeDelta(1000);
        List<Map<TransStation, TimeDelta>> inRouteL = validAs.stream()
                .map(c -> StationRetriever.getArrivableStations(c, chainToASched.get(c)
                        .getNextArrival(aTime), b.getTravelTimeFromPrev().plus(fuzz)))
                .collect(Collectors.toList());

        LoggingUtils.logMessage("RouteMapGen", "Got %s arrivable maps.", inRouteL.size());
        Optional<Map<TransStation, TimeDelta>> inRoute = inRouteL.stream().findAny();
        if (!inRoute.isPresent()) {
            LoggingUtils.logError("RouteMapGen", "Got no valid chains.");
            throw new RuntimeException();
        }

        return inRoute.get()
                .entrySet().stream()
                .sorted(Comparator.comparing(entry -> -1 * entry.getValue().getDeltaLong()))
                .map(Map.Entry::getKey)
                .map(st -> new TravelRouteNode.Builder().setPoint(st).build());
    }

    private static String mapRouteToPath(TravelRoute route, String color) {
        String pathStr = route.getRoute().stream()
                .map(nd -> String.format(RouteMapsPageConstants.PATH_ITEM_FORMAT, nd.getPt()
                        .getCoordinates()[0], nd.getPt().getCoordinates()[1]))
                .collect(StreamUtils.joinToString(", "));
        String name = route.hashCode() + "::" + route.getStart().getName() + "::" + route.getCurrentEnd().getName();
        return String.format(RouteMapsPageConstants.PATH_FORMAT, name, pathStr, color);
    }

    public static String generateIndivitualPage(TravelRoute route) {
        return generatePage(Lists.newArrayList(route));
    }
}
