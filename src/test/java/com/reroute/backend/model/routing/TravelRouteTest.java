package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimePoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class TravelRouteTest {

    private static final Random rng = new Random();

    @Test
    public void testAddNode() {
        TravelRoute route = new TravelRoute(new StartPoint(new double[] { 0, 0 }), TimePoint.now());

        TravelRouteNode.Builder nodeBuilder = new TravelRouteNode.Builder()
                .setWalkTime(10000);

        TravelRouteNode nextNode = nodeBuilder
                .setPoint(new DestinationLocation("Dest1", new LocationType("Dest", "Dest"), new double[] { 1, 1 }))
                .build();

        route.addNode(nextNode);

        try {
            TravelRouteNode extraStart = nodeBuilder
                    .setPoint(new StartPoint(new double[] { 2, 2 }))
                    .build();

            route.addNode(extraStart);

            Assert.fail("Added extra start.");
        } catch (IllegalArgumentException e) {
        }


        try {
            TravelRouteNode reverseNode = new TravelRouteNode.Builder()
                    .setWalkTime(-1000)
                    .setPoint(new DestinationLocation("Dest2", new LocationType("Dest", "Dest"), new double[] { 2, 2 }))
                    .build();

            route.addNode(reverseNode);

            Assert.fail("Added reverse node.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testReverse() {

        TravelRoute base = buildRandomRoute(10, 1000 * 60 * 60, false);
        TravelRoute reved = base.reverse();

        //Test timing info
        Assert.assertEquals(base.getTotalTime(), reved.getTotalTime().mul(-1));
        Assert.assertEquals(base.getTotalWalkTime(), reved.getTotalWalkTime().mul(-1));
        Assert.assertEquals(base.getTotalWaitTime(), reved.getTotalWaitTime().mul(-1));
        Assert.assertEquals(base.getTotalTravelTime(), reved.getTotalTravelTime().mul(-1));

        //Test that its the same interval
        Assert.assertEquals(base.getEndTime(), reved.getStartTime());
        Assert.assertEquals(base.getStartTime(), reved.getEndTime());

        //Test they start and end at the same places
        LocationPoint bEnd = base.getCurrentEnd();
        StartPoint rStart = reved.getStart();
        Assert.assertEquals(rStart.getName(), bEnd.getName());
        Assert.assertEquals(rStart.getType(), bEnd.getType());
        Assert.assertEquals(rStart.getCoordinates(), bEnd.getCoordinates());

        LocationPoint rEnd = reved.getCurrentEnd();
        StartPoint bStart = base.getStart();
        Assert.assertEquals(rEnd.getName(), bStart.getName());
        Assert.assertEquals(rEnd.getType(), bStart.getType());
        Assert.assertEquals(rEnd.getCoordinates(), bStart.getCoordinates());

        //Test they visit the same places
        List<TravelRouteNode> bRoute = base.getRoute();
        List<TravelRouteNode> rRoute = reved.getRoute();
        Assert.assertEquals(bRoute.size(), rRoute.size());

        int rtSize = bRoute.size();

        for (int i = 1; i < rtSize - 1; i++) {
            Assert.assertEquals(bRoute.get(i).getPt(), rRoute.get(rtSize - i - 1).getPt());
        }
    }

    public TravelRoute buildRandomRoute(int size, long maxTimes, boolean rev) {

        double[] startcoords = new double[] {
                rng.nextDouble() * 180 - 90,
                rng.nextDouble() * 360 - 180
        };

        TravelRoute rt = new TravelRoute(
                new StartPoint(startcoords),
                new TimePoint(Math.abs(rng.nextLong()), "GMT")
        );

        IntStream.range(1, size)
                .mapToObj(i -> buildRandomNode(maxTimes, rev))
                .forEach(rt::addNode);

        return rt;
    }

    public TravelRouteNode buildRandomNode(long maxTimes, boolean rev) {

        double[] randomcoords = new double[] {
                rng.nextDouble() * 180 - 90,
                rng.nextDouble() * 360 - 180
        };

        DestinationLocation nextNodeLoc = new DestinationLocation(
                String.format("Dest @ %f, %f", randomcoords[0], randomcoords[1]),
                new LocationType("Pitstop", "Pitstop"),
                randomcoords
        );

        long waitTime = rng.nextLong() % maxTimes;
        if (waitTime > 0 == rev) waitTime *= -1;

        long walkTime = rng.nextLong() % maxTimes;
        if (walkTime > 0 == rev) walkTime *= -1;

        long travelTime = rng.nextLong() % maxTimes;
        if (travelTime > 0 == rev) travelTime *= -1;

        return new TravelRouteNode.Builder()
                .setPoint(nextNodeLoc)
                .setWaitTime(waitTime)
                .setWalkTime(walkTime)
                .setTravelTime(travelTime)
                .build();
    }
}