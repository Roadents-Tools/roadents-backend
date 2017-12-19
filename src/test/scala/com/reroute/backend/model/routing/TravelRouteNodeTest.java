package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.StartPoint;
import org.junit.Assert;
import org.junit.Test;

public class TravelRouteNodeTest {

    @Test
    public void typeCheckers() {
        TravelRouteNode walkNode = new TravelRouteNode.Builder()
                .setWalkTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode travelNode = new TravelRouteNode.Builder()
                .setWaitTime(100)
                .setTravelTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode startNode = new TravelRouteNode.Builder()
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();

        Assert.assertTrue(walkNode.arrivesByFoot());
        Assert.assertFalse(walkNode.arrivesByTransportation());
        Assert.assertFalse(walkNode.isStart());
        Assert.assertFalse(walkNode.isDest());

        Assert.assertFalse(travelNode.arrivesByFoot());
        Assert.assertTrue(travelNode.arrivesByTransportation());
        Assert.assertFalse(travelNode.isStart());
        Assert.assertFalse(travelNode.isDest());


        Assert.assertFalse(startNode.arrivesByFoot());
        Assert.assertFalse(startNode.arrivesByTransportation());
        Assert.assertTrue(startNode.isStart());
        Assert.assertFalse(startNode.isDest());
    }

    @Test
    public void valueCheckers() {

        TravelRouteNode walkNode = new TravelRouteNode.Builder()
                .setWalkTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode travelNode = new TravelRouteNode.Builder()
                .setWaitTime(100)
                .setTravelTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode startNode = new TravelRouteNode.Builder()
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();

        Assert.assertEquals(100, walkNode.getWalkTimeFromPrev().getDeltaLong());
        Assert.assertEquals(0, walkNode.getWaitTimeFromPrev().getDeltaLong());
        Assert.assertEquals(0, walkNode.getTravelTimeFromPrev().getDeltaLong());
        Assert.assertEquals(100, walkNode.getTotalTimeToArrive().getDeltaLong());

        Assert.assertEquals(0, travelNode.getWalkTimeFromPrev().getDeltaLong());
        Assert.assertEquals(100, travelNode.getWaitTimeFromPrev().getDeltaLong());
        Assert.assertEquals(100, travelNode.getTravelTimeFromPrev().getDeltaLong());
        Assert.assertEquals(200, travelNode.getTotalTimeToArrive().getDeltaLong());

        Assert.assertEquals(0, startNode.getWalkTimeFromPrev().getDeltaLong());
        Assert.assertEquals(0, startNode.getWaitTimeFromPrev().getDeltaLong());
        Assert.assertEquals(0, startNode.getTravelTimeFromPrev().getDeltaLong());
        Assert.assertEquals(0, startNode.getTotalTimeToArrive().getDeltaLong());
    }

    @Test
    public void javaMethods() {
        TravelRouteNode walkNode = new TravelRouteNode.Builder()
                .setWalkTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode walkNode2 = new TravelRouteNode.Builder()
                .setWalkTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();
        TravelRouteNode travelNode = new TravelRouteNode.Builder()
                .setWaitTime(100)
                .setTravelTime(100)
                .setPoint(new StartPoint(new double[] { 0, 0 }))
                .build();

        Assert.assertTrue(walkNode.equals(walkNode2));
        Assert.assertEquals(walkNode.hashCode(), walkNode2.hashCode());
        Assert.assertEquals(walkNode.toString(), walkNode2.toString());

        Assert.assertFalse(walkNode.equals(travelNode));
        Assert.assertNotEquals(walkNode.hashCode(), travelNode.hashCode());
        Assert.assertNotEquals(walkNode.toString(), travelNode.toString());
    }

    @Test
    public void builder() {
        TravelRouteNode.Builder builder = new TravelRouteNode.Builder();
        try {
            TravelRouteNode wrong = builder.build();
            Assert.fail("Built invalid node.");
        } catch (IllegalStateException e) {
        }

    }
}