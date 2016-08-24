package org.tymit.restcontroller.jsonconvertion;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TravelRouteNode;

/**
 * Created by ilan on 8/22/16.
 */
public class TravelRouteNodeJsonConverterTest {

    @Test
    public void testJsonConverter() {
        TravelRouteNode testNode = new TravelRouteNode.Builder().setPoint(new StartPoint(new double[] { 4, 4 })).setTravelTime(5).setWaitTime(6).setWalkTime(7).build();

        TravelRouteNodeJsonConverter conv = new TravelRouteNodeJsonConverter();

        TravelRouteNode outpt = conv.fromJson(conv.toJson(testNode));

        Assert.assertEquals(testNode, outpt);
    }

}