package org.tymit.projectdonut.logic.logiccores;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.locations.providers.TestLocationProvider;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.stations.database.TestStationDb;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCoreTest {

    //895 Poplar Ave. Sunnyvale, CA
    private static final double[] CENTER = new double[]{37.358658, -122.008763};

    //Thu Jul 28 03:56:47 2016 UTC
    private static final TimeModel STARTTIME = TimeModel.fromUnixTime(1469678207308L);

    //Standard test max delta of 1 hour.
    private static final TimeModel MAXDELTA = TimeModel.fromUnixTimeDelta(1000L * 60L * 60L);

    //All of these points are within MAXDELTA of CENTER
    private static final double[][] WALKABLEPTS = new double[][]{
            new double[]{37.358658, -121.996699},
            new double[]{37.358658, -122.020827},
            new double[]{37.369902, -122.008763},
            new double[]{37.347414, -122.008763}
    };

    //We allow an error of up to 10 seconds due to rounding errors.
    private static final long MAX_ERROR = 10 * 1000L;

    @Before
    public void setupTest() {
        StationRetriever.setTestMode(true);
        LocationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testDonut() {
        //Constants
        final int STATIONS = 5;
        final int[] EVERY_TEN_MINS = new int[]{0, 10, 20, 30, 40, 50};

        long startTime = STARTTIME.getUnixTime();
        double latitude = CENTER[0];
        double longitude = CENTER[1];
        long timeDelta = MAXDELTA.getUnixTimeDelta();

        //Build test data
        Set<TransStation> allStations = new HashSet<>();
        Set<TransStation> testStations = new HashSet<>();
        for (int walkableIndex = 0; walkableIndex < WALKABLEPTS.length; walkableIndex++) {
            TransChain testChain = new TransChain("TEST CHAIN: " + walkableIndex);

            List<TimeModel> walkableSchedule = new ArrayList<>(EVERY_TEN_MINS.length);
            for (int min : EVERY_TEN_MINS) {
                TimeModel item = TimeModel.empty().set(TimeModel.MINUTE, min);
                walkableSchedule.add(item);
            }
            String walkableName = String.format("TEST STATION: %d,W", walkableIndex);
            double[] walkableCoords = WALKABLEPTS[walkableIndex];
            TransStation walkable = new TransStation(walkableName, walkableCoords, walkableSchedule, testChain);
            allStations.add(walkable);
            testStations.add(walkable);

            for (int stationNum = 1; stationNum < STATIONS; stationNum++) {
                List<TimeModel> arrivableSchedule = new ArrayList<>(EVERY_TEN_MINS.length);
                for (int min : EVERY_TEN_MINS) {
                    TimeModel item = TimeModel.empty().set(TimeModel.MINUTE, min + stationNum);
                    arrivableSchedule.add(item);
                }
                String arrivableName = String.format("TEST STATION: %d, %d", walkableIndex, stationNum);
                double[] coords = new double[]{((stationNum + 1) * CENTER[0]) % 90 + walkableIndex, ((stationNum + 1) * CENTER[1]) % 180 + walkableIndex};
                TransStation arrivable = new TransStation(arrivableName, coords, arrivableSchedule, testChain);
                allStations.add(arrivable);
            }

        }
        TestStationDb.setTestStations(allStations);

        //Setup the args object
        Map<String, Object> args = new HashMap<>();
        args.put("starttime", startTime);
        args.put("latitude", latitude);
        args.put("longitude", longitude);
        args.put("timedelta", timeDelta);
        args.put("type", "food");
        args.put("test", true);

        //Run the logic core
        Map<String, List<Object>> output = ApplicationRunner.runApplication("DONUT", args);


        //Check the results
        List<Object> routes = output.get("ROUTES");

        //We should have 1 transit route for the start point, and then 1 for each station within each chain.
        //We have STATIONS stations per WALKABLEPTS.length chains. We then multiply by DEFAULT_POINTS_PER_QUERY
        // to get the location routes.
        int expectedRoutes = (1 + STATIONS * WALKABLEPTS.length) * TestLocationProvider.DEFAULT_POINTS_PER_QUERY;
        Assert.assertEquals(expectedRoutes, routes.size());

        //Test that we have no destination dupes.
        Set<DestinationLocation> allDests = new HashSet<>();
        for (Object o : routes) {
            TravelRoute route = (TravelRoute) o;
            DestinationLocation dest = route.getDestination();
            Assert.assertTrue(!allDests.contains(dest));
            allDests.add(dest);
        }

        //Test that our error margin is small enough
        routes.stream()
                .map(o -> (TravelRoute) o)
                .forEach(route -> {
                    long savedDelta = route.getTotalTime();
                    long calcDelta = 0;
                    int routeSize = route.getRoute().size();
                    for (int i = 1; i < routeSize; i++) {
                        LocationPoint current = route.getRoute().get(i).getPt();
                        LocationPoint prev = route.getRoute().get(i - 1).getPt();
                        if (!(prev instanceof TransStation
                                && current instanceof TransStation
                                && ((TransStation) prev).getChain().equals(((TransStation) current).getChain())
                        )) {
                            double distance = LocationUtils.distanceBetween(prev.getCoordinates(), current.getCoordinates(), true);
                            long addedTime = LocationUtils.distanceToWalkTime(distance, true);
                            calcDelta += addedTime;
                            continue;
                        }
                        TransStation cStation = (TransStation) current;
                        TransStation pStation = (TransStation) prev;
                        TimeModel pArrival = pStation.getNextArrival(TimeModel.fromUnixTime(startTime + calcDelta));
                        TimeModel cArrival = cStation.getNextArrival(pArrival);
                        calcDelta += (cArrival.getUnixTime() - calcDelta - startTime);
                    }
                    Assert.assertTrue(calcDelta - savedDelta < MAX_ERROR);
                });
    }

    @After
    public void cleanUpTest() {
        StationRetriever.setTestMode(false);
        LocationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}