package org.tymit.projectdonut.logic.logiccores;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.locations.providers.TestLocationProvider;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.stations.test.TestStationDb;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCoreTest {

    //895 Poplar Ave. Sunnyvale, CA
    private static final double[] CENTER = new double[]{37.358658, -122.008763};

    //Thu Jul 28 03:56:47 2016 UTC
    private static final TimePoint STARTTIME = new TimePoint(1469678207308L, "America/Los_Angeles");

    //Standard test max delta of 1 hour.
    private static final TimeDelta MAXDELTA = new TimeDelta(1000L * 60L * 60L);

    //All of these points are within MAXDELTA of CENTER
    private static final double[][] WALKABLEPTS = new double[][]{
            new double[]{37.358658, -121.996699},
            new double[]{37.358658, -122.020827},
            new double[]{37.369902, -122.008763},
            new double[]{37.347414, -122.008763}
    };

    //We allow an error of up to 10 seconds due to rounding errors.
    private static final long MAX_ERROR = 10 * 1000L;

    private static final LocationType TEST_TYPE = new LocationType("food", "food");

    private static final int STATIONS = 5;

    private static final double RNG = 0.001;

    @Before
    public void setupTest() {
        StationRetriever.setTestMode(true);
        LocationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testDonut() {

        long startTime = STARTTIME.getUnixTime();
        double latitude = CENTER[0];
        double longitude = CENTER[1];
        long timeDelta = MAXDELTA.getDeltaLong();

        //Build test data
        Set<TransStation> allStations = buildTestStations();
        TestStationDb.setTestStations(allStations);
        TestLocationProvider.setTestLocations(buildTestDests(allStations, new StartPoint(CENTER)));

        //Setup the args object
        Map<String, Object> args = new HashMap<>();
        args.put("starttime", startTime);
        args.put("latitude", latitude);
        args.put("longitude", longitude);
        args.put("timedelta", timeDelta);
        args.put("type", TEST_TYPE.getEncodedname());
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


        routes.stream().map(o -> (TravelRoute) o)

                //Test for a middleman issue
                .peek(rt -> Assert.assertFalse(DonutLogicSupport.isMiddleMan(rt)))

                //Test that our error margin is small enough
                .forEach(route -> {
                    long savedDelta = route.getTotalTime().getDeltaLong();
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
                        TimePoint pArrival = pStation.getNextArrival(new TimePoint(startTime + calcDelta, "America/Los_Angeles"));
                        TimePoint cArrival = cStation.getNextArrival(pArrival);
                        calcDelta += (cArrival.getUnixTime() - calcDelta - startTime);
                    }
                    Assert.assertTrue(calcDelta - savedDelta < MAX_ERROR);
                });
    }

    private static Set<DestinationLocation> buildTestDests(Collection<? extends TransStation> stations, StartPoint center) {
        final String nameFormat = "Test Dest: QueryCenter = (%f,%f), Type = %s, Additive = (%f,%f)";
        Set<LocationPoint> allPts = new HashSet<>();
        allPts.addAll(stations);
        allPts.add(center);
        return allPts.stream()
                .flatMap(st -> Stream.of(
                        new DestinationLocation(
                                String.format(nameFormat, st.getCoordinates()[0], st
                                        .getCoordinates()[1], TEST_TYPE.getVisibleName(), 0.0, RNG),
                                TEST_TYPE,
                                new double[] { st.getCoordinates()[0], st.getCoordinates()[1] + RNG }),
                        new DestinationLocation(
                                String.format(nameFormat, st.getCoordinates()[0], st
                                        .getCoordinates()[1], TEST_TYPE.getVisibleName(), -1 * RNG, 0.0),
                                TEST_TYPE,
                                new double[] { st.getCoordinates()[0] - RNG, st.getCoordinates()[1] }),
                        new DestinationLocation(
                                String.format(nameFormat, st.getCoordinates()[0], st
                                        .getCoordinates()[1], TEST_TYPE.getVisibleName(), RNG, 0.0),
                                TEST_TYPE,
                                new double[] { st.getCoordinates()[0] + RNG, st.getCoordinates()[1] }),
                        new DestinationLocation(
                                String.format(nameFormat, st.getCoordinates()[0], st
                                        .getCoordinates()[1], TEST_TYPE.getVisibleName(), 0.0, -1 * RNG),
                                TEST_TYPE,
                                new double[] { st.getCoordinates()[0], st.getCoordinates()[1] - RNG })
                        )
                )
                .collect(Collectors.toSet());
    }

    private static Set<TransStation> buildTestStations() {
        Set<TransStation> allStations = new HashSet<>();
        for (int walkableIndex = 0; walkableIndex < WALKABLEPTS.length; walkableIndex++) {
            TransChain testChain = new TransChain("TEST CHAIN: " + walkableIndex);

            List<SchedulePoint> walkableSchedule = new ArrayList<>();
            for (int h = 0; h < 23; h++) {
                for (int min = 0; min < 60; min += 10) {
                    walkableSchedule.add(new SchedulePoint(h, min, 0, null, 60));
                }
            }
            String walkableName = String.format("TEST STATION: %d,W", walkableIndex);
            double[] walkableCoords = WALKABLEPTS[walkableIndex];
            TransStation walkable = new TransStation(walkableName, walkableCoords, walkableSchedule, testChain);
            allStations.add(walkable);

            for (int stationNum = 1; stationNum < STATIONS; stationNum++) {
                List<SchedulePoint> arrivableSchedule = new ArrayList<>();
                for (int h = 0; h < 23; h++) {
                    for (int min = 0; min < 60; min += 10) {
                        arrivableSchedule.add(new SchedulePoint(h, min + stationNum, 0, null, 60));
                    }
                }
                String arrivableName = String.format("TEST STATION: %d, %d", walkableIndex, stationNum);
                double[] coords = new double[] { ((stationNum + 1) * CENTER[0]) % 90 + walkableIndex, ((stationNum + 1) * CENTER[1]) % 180 + walkableIndex };
                TransStation arrivable = new TransStation(arrivableName, coords, arrivableSchedule, testChain);
                allStations.add(arrivable);
            }

        }
        return allStations;
    }

    @After
    public void cleanUpTest() {
        StationRetriever.setTestMode(false);
        LocationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }
}