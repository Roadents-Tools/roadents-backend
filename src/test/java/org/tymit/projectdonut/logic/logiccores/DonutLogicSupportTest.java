package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.locations.providers.TestLocationProvider;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.stations.database.TestStationDb;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ilan on 7/27/16.
 */
public class DonutLogicSupportTest {

    //895 Poplar Ave. Sunnyvale, CA
    private static final double[] CENTER = new double[]{37.358658, -122.008763};

    //Thu Jul 28 03:56:47 2016 UTC
    private static final TimeModel STARTTIME = TimeModel.fromUnixTime(1469678207308l);

    //Standard test max delta of 1 hour.
    private static final TimeModel MAXDELTA = TimeModel.fromUnixTimeDelta(1000l * 60l * 60l);

    //All of these points are within MAXDELTA of CENTER
    private static final double[][] WALKABLEPTS = new double[][]{
            new double[]{37.358658, -121.996699},
            new double[]{37.358658, -122.020827},
            new double[]{37.369902, -122.008763},
            new double[]{37.347414, -122.008763},
            new double[]{37.357414, -122.008763}
    };

    @Before
    public void setUpEnv() {
        LocationRetriever.setTestMode(true);
        StationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void buildStationRouteList() throws Exception {
        //Constants
        final int STATIONS = 5;
        final int[] EVERY_TEN_MINS = new int[]{0, 10, 20, 30, 40, 50};

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

        Set<String> uniqueCoords = allStations.parallelStream()
                .map(DonutLogicSupport::getLocationTag)
                .collect(Collectors.toSet());



        TravelRoute initRoute = new TravelRoute(new StartPoint(CENTER), STARTTIME);
        Map<String, TravelRoute> allRoutes = new ConcurrentHashMap<>();
        allRoutes.put(DonutLogicSupport.getLocationTag(initRoute.getCurrentEnd()), initRoute);


        DonutLogicSupport.buildStationRouteList(Lists.newArrayList(initRoute), STARTTIME, MAXDELTA, allRoutes);

        Set<String> allRouteTags = allRoutes.keySet();
        Set<String> missing = new HashSet<>(uniqueCoords).stream()
                .filter(tag -> !allRouteTags.contains(tag))
                .collect(Collectors.toSet());

        if (missing.size() > 0) {
            System.out.printf("Have: \n");
            for (String tag : allRouteTags) {
                System.out.printf("  %s\n", tag);
            }
            System.out.printf("Missing: \n");
            for (String tag : missing) {
                System.out.printf("  %s\n", tag);
            }
        }

        Assert.assertEquals(uniqueCoords.size() + 1, allRoutes.size());

    }


    @Test
    public void getAllChainsForStop() throws Exception {
        final int maxChains = 10;
        List<TransChain> testChains = IntStream.range(0, maxChains)
                .mapToObj(num -> new TransChain("TESTCHAIN: " + num))
                .collect(Collectors.toList());


        Map<double[], Set<TransStation>> stations = new HashMap<>();
        Arrays.stream(WALKABLEPTS)
                .map(pt -> {
                    stations.put(pt, new HashSet<>());
                    return pt;
                })
                .forEach(pt -> testChains.stream()
                        .map(chain -> new TransStation("TEST STATION " + Arrays.toString(pt) + ' ' + chain.getName(), pt, null, chain))
                        .forEach(station -> stations.get(pt).add(station)));
        TestStationDb.setTestStations(stations.values().stream().reduce(new HashSet<>(), (stations1, stations2) -> {
            stations1.addAll(stations2);
            return stations1;
        }));
        stations.values()
                .forEach(stationList -> stationList.stream()
                        .forEach(station -> Assert.assertEquals(stationList, DonutLogicSupport.getAllChainsForStop(station))));
    }

    @Test
    public void getWalkableDestinations() throws Exception {
        final LocationType testType = new LocationType("TestType", "TestType");
        Set<TravelRouteNode> expected = Sets.newHashSetWithExpectedSize(WALKABLEPTS.length);
        Set<DestinationLocation> testLocations = Sets.newHashSetWithExpectedSize(WALKABLEPTS.length);
        for (double[] walkable : WALKABLEPTS) {
            DestinationLocation testStation = new DestinationLocation(String.format("Test: %f,%f", walkable[0], walkable[1]), testType, walkable);
            double dist = LocationUtils.distanceBetween(walkable, CENTER, true);
            long delta = LocationUtils.distanceToWalkTime(dist, true);
            expected.add(new TravelRouteNode.Builder().setPoint(testStation).setWalkTime(delta).build());
            testLocations.add(testStation);
        }

        TestLocationProvider.setTestLocations(testLocations);

        Set<TravelRouteNode> results = DonutLogicSupport.getWalkableDestinations(new StartPoint(CENTER), MAXDELTA, testType);
        Assert.assertEquals(expected, results);
    }

    @Test
    public void getAllPossibleStations() throws Exception {

        //Constants
        final int STATIONS = 5;
        final int[] EVERY_TEN_MINS = new int[]{0, 10, 20, 30, 40, 50};

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
                double[] coords = new double[]{((stationNum + 1) * CENTER[0]) % 90, ((stationNum + 1) * CENTER[1]) % 180};
                TransStation arrivable = new TransStation(arrivableName, coords, arrivableSchedule, testChain);
                allStations.add(arrivable);
            }

        }
        TestStationDb.setTestStations(allStations);

        //Run method
        Set<TravelRouteNode> fromStart = DonutLogicSupport.getAllPossibleStations(new StartPoint(CENTER), STARTTIME, MAXDELTA);
        List<Set<TravelRouteNode>> fromWalkables = testStations.stream()
                .map(station -> DonutLogicSupport.getAllPossibleStations(station, STARTTIME, MAXDELTA))
                .collect(Collectors.toList());

        //Check output
        Assert.assertEquals(WALKABLEPTS.length, fromStart.size());

        //We set it to STATIONS+WALKABLEPTS.length-1 because our generator
        //creates stations in the same place for each chain, so each station
        //has WALKABLEPTS.length-1 stations nearby.
        fromWalkables.forEach(result -> Assert.assertEquals(STATIONS + WALKABLEPTS.length - 1, result.size()));
    }

    @Test
    public void getWalkableStations() throws Exception {

        Set<TravelRouteNode> expected = new HashSet<>(WALKABLEPTS.length);
        Set<TransStation> testStations = new HashSet<>(WALKABLEPTS.length);
        for (double[] walkable : WALKABLEPTS) {
            TransStation testStation = new TransStation(String.format("Test: %f,%f", walkable[0], walkable[1]), walkable);
            double dist = LocationUtils.distanceBetween(walkable, CENTER, true);
            long delta = LocationUtils.distanceToWalkTime(dist, true);
            expected.add(new TravelRouteNode.Builder().setWalkTime(delta).setPoint(testStation).build());
            testStations.add(testStation);
        }

        TestStationDb.setTestStations(testStations);

        Set<TravelRouteNode> results = DonutLogicSupport.getWalkableStations(new StartPoint(CENTER), MAXDELTA);
        Assert.assertEquals(expected, results);
    }

    @Test
    public void getArrivableStations() throws Exception {

        //Constants
        final int STATIONS = 5;
        final int[] EVERY_TEN_MINS = new int[]{0, 10, 20, 30, 40, 50};

        //Set up a test chain with test stations that arrive every 10 mins
        TransChain chain = new TransChain("TEST CHAIN");
        List<TransStation> testStations = new ArrayList<>(STATIONS);
        for (int i = 0; i < STATIONS; i++) {
            List<TimeModel> schedule = new ArrayList<>(6);
            for (int MIN : EVERY_TEN_MINS) {
                schedule.add(TimeModel.empty().set(TimeModel.MINUTE, MIN + i));
            }
            double[] location = WALKABLEPTS[i % WALKABLEPTS.length];
            TransStation testStation = new TransStation("TEST STATION " + i, location, schedule, chain);
            testStations.add(testStation);
        }
        TestStationDb.setTestStations(testStations);

        //Perform the test
        Set<TravelRouteNode> arrivable = DonutLogicSupport.getArrivableStations(testStations.get(0), STARTTIME, MAXDELTA);

        //Check
        Assert.assertEquals(STATIONS-1, arrivable.size());
        for (TravelRouteNode stationNode : arrivable) {

            TransStation station = (TransStation) stationNode.getPt();

            int result = STARTTIME.addUnixTime(stationNode.getTotalTimeToArrive()).get(TimeModel.MINUTE) % 10;
            int expected = station.getSchedule().get(0).get(TimeModel.MINUTE) % 10;
            Assert.assertEquals(expected, result);
        }
    }

    @After
    public void undoSetup() {
        LocationRetriever.setTestMode(false);
        StationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}