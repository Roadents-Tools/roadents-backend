package com.reroute.displayers.testdisplay.listtestdisplayer;

import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.utils.LocationUtils;

import java.util.List;

/**
 * Created by ilan on 1/3/17.
 */
public class TestDisplayer {

    public static String buildDisplay(List<TravelRoute> routes) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append(TestDisplayConstants.HEAD);
        builder.append("\n<body>\n");

        routes.stream()
                .map(TestDisplayer::routeToList)
                .forEach(builder::append);

        builder.append("<script>\n").append(TestDisplayConstants.JAVASCRIPT).append("</script>\n");
        return builder.toString();
    }

    public static String routeToList(TravelRoute route) {
        long totalMillis = route.getTotalTime().getDeltaLong();
        long hours = totalMillis / 3600000L;
        long mins = totalMillis / 60000L % 60;
        StringBuilder builder = new StringBuilder();

        builder.append(String.format(TestDisplayConstants.ROUTE_TITLE_FORMAT, route.getDestination()
                        .getName(), hours, mins,
                LocationUtils.distanceBetween(route.getStart(), route.getDestination()).inMiles(), route.getRoute()
                        .size() - 2)
        );

        route.getRoute().stream()
                .map(node -> String.format(TestDisplayConstants.ROUTE_ELEMENT_FORMAT,
                        node.getWalkTimeFromPrev()
                                .getDeltaLong() / 60000, node.getWaitTimeFromPrev()
                                .getDeltaLong() / 60000, node.getTravelTimeFromPrev()
                                .getDeltaLong() / 60000,
                        node.getPt().getName(), node.getPt()
                                .getCoordinates()[0], node.getPt()
                                .getCoordinates()[1],
                        route.getTimeAtNode(node)
                                .getHour(), route.getTimeAtNode(node)
                                .getMinute())
                )
                .forEach(builder::append);

        builder.append(TestDisplayConstants.ROUTE_FOOTER);

        return builder.toString();
    }
}
