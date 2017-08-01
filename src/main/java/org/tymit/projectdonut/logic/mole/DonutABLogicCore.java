package org.tymit.projectdonut.logic.mole;

import org.tymit.projectdonut.logic.donut.DonutLogicSupport;
import org.tymit.projectdonut.logic.interfaces.LogicCore;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.model.routing.TravelRouteNode;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Created by ilan on 7/10/16.
 */
public class DonutABLogicCore implements LogicCore {

    public static final String TAG = "DONUTAB";
    public static final String START_TIME_TAG = "starttime";
    public static final String A_LAT_TAG = "latitude";
    public static final String A_LONG_TAG = "longitude";
    public static final String B_LAT_TAG_FORMAT = "latitude%d";
    public static final String B_LONG_TAG = "longitude%d";

    public static final String ROUTE_LIST_TAG = "ROUTES";

    private static final LocationType FILLER_TYPE = new LocationType("Destination", "Destination");

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {

        //Get the args
        long startUnixTime = (long) args.get(START_TIME_TAG);
        TimePoint startTime = new TimePoint(startUnixTime, "America/Los_Angeles");
        double startLat = (double) args.get(A_LAT_TAG);
        double startLong = (double) args.get(A_LONG_TAG);

        List<DestinationLocation> ends = new ArrayList<>();
        for (int i = 2; args.containsKey(String.format(B_LAT_TAG_FORMAT, i)); i++) {
            double curLat = (double) args.get(String.format(B_LAT_TAG_FORMAT, i));
            double curLng = (double) args.get(String.format(B_LONG_TAG, i));
            ends.add(new DestinationLocation(
                    String.format("end %d", i),
                    FILLER_TYPE,
                    new double[] { curLat, curLng }
            ));
        }

        //Run the core
        List<TravelRoute> destsToRoutes = runDonutRouting(
                new StartPoint(new double[] { startLat, startLong }),
                startTime,
                ends
        );

        //Build the output
        Map<String, List<Object>> output = new HashMap<>();
        if (LoggingUtils.hasErrors()) {
            List<Object> errs = new ArrayList<>(LoggingUtils.getErrors());
            output.put("ERRORS", errs);
        }
        output.put(ROUTE_LIST_TAG, new ArrayList<>(destsToRoutes));
        return output;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    public List<TravelRoute> runDonutRouting(StartPoint start, TimePoint startTime, List<DestinationLocation> ends) {

        //Prepare the call
        for (LocationPoint end : ends) {
            double centerlag = (start.getCoordinates()[0] + end.getCoordinates()[0]) / 2;
            double centerlng = (start.getCoordinates()[1] + end.getCoordinates()[1]) / 2;
            final double[] center = new double[] { centerlag, centerlng };
            final TimeDelta maxDelta = LocationUtils.timeBetween(start, end);
            StationRetriever.prepareWorld(new StartPoint(center), startTime, maxDelta);
        }

        TimeDelta maxTimeDelta = ends.stream()
                .map(end -> LocationUtils.timeBetween(start, end))
                .max(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(new TimeDelta(-1));

        //Get the station routes
        Set<TravelRoute> stationRoutes = DonutLogicSupport.buildStationRouteList(start, startTime, maxTimeDelta);
        LoggingUtils.logMessage(getClass().getName(), "Got %d station routes.", stationRoutes.size());

        //Optimize and attach the ends
        List<TravelRoute> destRoutes = new ArrayList<>();
        for (LocationPoint end : ends) {
            TravelRoute base = stationRoutes.stream()
                    .filter(route -> !DonutLogicSupport.isMiddleMan(route)) //Filter the middlemen immediately
                    .filter(route -> !route.getRoute()
                            .get(route.getRoute().size() - 1)
                            .arrivesByFoot()) //Would be a future middleman
                    .min(Comparator.comparing(route -> LocationUtils.timeBetween(route.getCurrentEnd(), end)
                            .getDeltaLong()))
                    .orElse(stationRoutes.iterator().next()) //Pick a random on error
                    .clone();

            TravelRouteNode endNode = new TravelRouteNode.Builder()
                    .setPoint(end)
                    .setWalkTime(LocationUtils.timeBetween(base.getCurrentEnd(), end).getDeltaLong())
                    .build();
            TravelRoute route = base.setDestinationNode(endNode);
            destRoutes.add(route);
        }
        LoggingUtils.logMessage(getClass().getName(),
                "Got %d -> %d dest routes.", stationRoutes.size(), destRoutes.size()
        );
        return destRoutes;
    }
}
