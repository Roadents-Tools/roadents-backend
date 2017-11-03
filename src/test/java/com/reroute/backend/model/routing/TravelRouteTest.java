package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Random;
import java.util.stream.IntStream;

public class TravelRouteTest {

    private static final Random rng = new Random();


    @Test
    public void testRoute() {

        TravelRoute base = buildRandomRoute(10, 1000 * 60 * 60, false);

        TimeDelta totalWalk = base.getRoute().stream()
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);

        TimeDelta totalWait = base.getRoute().stream()
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);

        TimeDelta totalTravel = base.getRoute().stream()
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);

        TimeDelta totalTime = base.getRoute().stream()
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);

        Assert.assertEquals(totalWalk, base.getWalkTime());
        Assert.assertEquals(totalWait, base.getWaitTime());
        Assert.assertEquals(totalTravel, base.getTravelTime());
        Assert.assertEquals(totalTime, base.getTime());
        Assert.assertEquals(base.getStartTime().plus(totalTime), base.getEndTime());
        Assert.assertEquals(10, base.getRoute().size());
    }

    private TravelRoute buildRandomRoute(int size, long maxTimes, boolean rev) {

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

    private TravelRouteNode buildRandomNode(long maxTimes, boolean rev) {

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