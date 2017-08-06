package com.reroute.backend.jsonconvertion;

import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by ilan on 7/17/16.
 */
public class TravelRouteJsonConverterTest {

    private TravelRoute testRoute;

    @Before
    public void setUpTestRoute() {

        LocationRetriever.setTestMode(true);
        StationRetriever.setTestMode(true);
        //TODO Fix this test and its lack of station retrieval
        LoggingUtils.setPrintImmediate(true);

        final double latitude = 37.358658;
        final double longitude = -122.008763;
        StartPoint startPoint = new StartPoint(new double[]{latitude, longitude});
        TimePoint startTime = new TimePoint(System.currentTimeMillis(), "America/Los_Angeles");
        testRoute = new TravelRoute(startPoint, startTime);
        Random rng = new Random();

        final double range = 50;
        for (int i = 0; i < 5; i++) {
            TransStation currentStation = new TransStation("t" + i, new double[] { latitude + (i + 1) * 10 * rng
                    .nextDouble(), longitude + (i + 1) * 10 * rng.nextDouble() });
            TravelRouteNode stationNode = new TravelRouteNode.Builder().setPoint(currentStation).setTravelTime(1).setWaitTime(1).build();
            if (testRoute.isInRoute(stationNode.getPt())) {
                testRoute.addNode(stationNode);
            } else continue;
            List<TransStation> stationsInChain =
                    new ArrayList<>(StationRetriever.getArrivableStations(currentStation.getChain(), TimePoint.NULL, new TimeDelta(1000 * 60 * 60 * 1000L))
                            .keySet());
            TransStation chainStation = stationsInChain.get(i % stationsInChain.size());
            TravelRouteNode chainStationNode = new TravelRouteNode.Builder().setPoint(chainStation).setTravelTime(1).setWaitTime(1).build();
            testRoute.addNode(chainStationNode);
        }

        LocationType testType = new LocationType("food", "food");
        List<DestinationLocation> locations = LocationRetriever.getLocations(testRoute.getCurrentEnd(), new Distance(range, DistanceUnits.MILES), testType, null);
        testRoute.setDestinationNode(new TravelRouteNode.Builder().setWalkTime(1).setPoint(locations.get(0)).build());
    }

    @Test
    public void testBackAndForth() {
        String routeJson = new TravelRouteJsonConverter()
                .toJson(testRoute);
        TravelRoute fromJson = new TravelRouteJsonConverter()
                .fromJson(routeJson);
        Assert.assertEquals(testRoute, fromJson);
    }

    @After
    public void cleanUp() {
        LocationRetriever.setTestMode(false);
        StationRetriever.setTestMode(false);
        LoggingUtils.setPrintImmediate(false);
    }

}