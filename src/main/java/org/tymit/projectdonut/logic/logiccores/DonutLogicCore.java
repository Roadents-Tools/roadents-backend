package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;


/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCore implements LogicCore {

    public static final String TAG = "DONUT";
    public static final String START_TIME_TAG = "starttime";
    public static final String LAT_TAG = "latitude";
    public static final String LONG_TAG = "longitude";
    public static final String TYPE_TAG = "type";
    public static final String TIME_DELTA_TAG = "timedelta";

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        //Get the args
        long startUnixTime = (long) args.get(START_TIME_TAG);
        TimeModel startTime = TimeModel.fromUnixTime(startUnixTime);
        double startLat = (double) args.get(LAT_TAG);
        double startLong = (double) args.get(LONG_TAG);
        long maxUnixTimeDelta = (long) args.get(TIME_DELTA_TAG);
        TimeModel maxTimeDelta = TimeModel.fromUnixTimeDelta(maxUnixTimeDelta);
        LocationType type = new LocationType((String) args.get(TYPE_TAG), (String) args.get(TYPE_TAG));

        //Run the core
        Map<DestinationLocation, TravelRoute> destsToRoutes = runDonut(
                new StartPoint(new double[]{startLat, startLong}),
                startTime,
                maxTimeDelta,
                type
        );

        //Build the output
        Map<String, List<Object>> output = new HashMap<>();
        if (LoggingUtils.hasErrors()) {
            List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
            output.put("ERRORS", errs);
        }
        output.put("DESTS", new ArrayList<>());
        output.put("ROUTES", new ArrayList<>());
        for (TravelRoute route : destsToRoutes.values()) {
            output.get("DESTS").add(route.getDestination());
            output.get("ROUTES").add(route);
        }
        return output;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    /**
     * Runs the donut logic core.
     *
     * @param center       the starting central location to use
     * @param startTime    the time to start at
     * @param maxTimeDelta the maximum time for each route
     * @param type         the type of destination to find
     * @return all possible destinations mapped to the best route to get there
     */
    private Map<DestinationLocation, TravelRoute> runDonut(StartPoint center, TimeModel startTime, TimeModel maxTimeDelta, LocationType type) {

        Set<TravelRoute> stationRoutes = DonutLogicSupport.buildStationRouteList(center, startTime, maxTimeDelta);
        LoggingUtils.logMessage(getClass().getName(), "Got %d station routes.", stationRoutes.size());

        Set<TravelRoute> destRoutes = stationRoutes.stream()
                .filter(route -> maxTimeDelta.getUnixTimeDelta() >= route.getTotalTime())
                .flatMap(route -> {
                    TimeModel trueDelta = maxTimeDelta.addUnixTime(-1 * route.getTotalTime());
                    Set<TravelRouteNode> possibleDests = DonutLogicSupport.getWalkableDestinations(route.getCurrentEnd(), trueDelta, type);
                    return DonutLogicSupport.addDestinationsToRoute(route, possibleDests).stream();
                })
                .collect(Collectors.toSet());
        LoggingUtils.logMessage(getClass().getName(), "Got %d dest routes.", destRoutes.size());

        ConcurrentMap<DestinationLocation, TravelRoute> destToShortest = new ConcurrentHashMap<>();
        destRoutes.stream()

                //See server issue #48 to see more information of the middleman timing issue
                .map(route -> {
                    if (route.getRoute().size() != 3) return route;
                    long destWalkTime = LocationUtils.timeBetween(route.getStart()
                            .getCoordinates(), route.getDestination().getCoordinates());
                    if (destWalkTime > maxTimeDelta.getUnixTimeDelta()) {
                        LoggingUtils.logError(getTag(), "Failed to find or create valid route.\nWalk time: %d\nFake time: %d", destWalkTime, route
                                .getTotalTime());
                        return route;
                    }
                    TravelRouteNode destNode = new TravelRouteNode.Builder().setPoint(route.getDestination())
                            .setWalkTime(destWalkTime)
                            .build();
                    return new TravelRoute(route.getStart(), route.getStartTime()).setDestinationNode(destNode);
                })
                .filter(travelRoute -> travelRoute.getRoute().size() != 3)

                .filter(route -> destToShortest.putIfAbsent(route.getDestination(), route) != null)
                .filter(route -> destToShortest.get(route.getDestination()).getTotalTime() > route.getTotalTime())
                .forEach(route -> destToShortest.put(route.getDestination(), route));
        return destToShortest;
    }
}
