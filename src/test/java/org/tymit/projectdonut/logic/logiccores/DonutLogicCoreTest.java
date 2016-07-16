package org.tymit.projectdonut.logic.logiccores;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.*;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.*;

/**
 * Created by ilan on 7/10/16.
 */
public class DonutLogicCoreTest {


    private static final long MAX_ERROR = 30 * 1000l;

    @Before
    public void setupTest() {
        StationRetriever.setTestMode(true);
        LocationRetriever.setTestMode(true);
        LoggingUtils.setPrintImmediate(true);
        LoggingUtils.logMessage("SETUP", "SETUP");
    }

    @Test
    public void testDonut() {

        long startTime = System.currentTimeMillis();
        double latitude = 37.358658;
        double longitude = -122.008763;
        long timeDelta = 1000l * 60l * 60l * 24l; //1 day

        System.out.printf("TEST CONSTS:\n Start Time = %d\n Time Delta = %d\n Center = (%f,%f)\n",
                startTime, timeDelta, latitude, longitude
        );

        Map<String, Object> args = new HashMap<>();
        args.put("starttime", startTime);
        args.put("latitude", latitude);
        args.put("longitude", longitude);
        args.put("timedelta", timeDelta);
        args.put("type", "food");

        Map<String, List<Object>> output = ApplicationRunner.runApplication("DONUT", args);
        List<Object> routes = output.get("ROUTES");

        //Test that we have no destination dupes.
        Set<DestinationLocation> allDests = new HashSet<>();
        for (Object o : routes) {
            TravelRoute route = (TravelRoute) o;
            DestinationLocation dest = route.getDestination();
            Assert.assertTrue(!allDests.contains(dest));
            allDests.add(dest);
        }

        //Test that our error margin is small enough that it doesn't really matter.
        routes.stream()
                .map(o -> (TravelRoute) o)
                .forEach(route -> {
                    long savedDelta = (long) route.getCosts().getOrDefault("timedelta", 0l);
                    long calcDelta = 0;
                    int routeSize = route.getRoute().size();
                    for (int i = 1; i < routeSize; i++) {
                        LocationPoint current = route.getRoute().get(i);
                        LocationPoint prev = route.getRoute().get(i - 1);
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
        LoggingUtils.logMessage("CLEANUP", "CLEANUP");
        LoggingUtils.printLog();
        StationRetriever.setTestMode(false);
        LocationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}