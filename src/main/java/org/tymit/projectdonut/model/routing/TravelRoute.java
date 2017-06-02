package org.tymit.projectdonut.model.routing;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private final List<TravelRouteNode> routeNodes;
    private final TimePoint startTime;
    private TravelRouteNode end;

    public TravelRoute(StartPoint start, TimePoint startTime) {
        TravelRouteNode stNode = new TravelRouteNode.Builder().setPoint(start)
                .build();
        routeNodes = new ArrayList<>();
        routeNodes.add(stNode);
        this.startTime = startTime;
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public TimeDelta getTotalTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public List<TravelRouteNode> getRoute() {
        List<TravelRouteNode> route = new ArrayList<>();
        route.addAll(routeNodes);
        if (end != null) route.add(end);
        return route;
    }

    public TimeDelta getTotalTimeAtNode(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        return nodeIndex < 0 ? TimeDelta.NULL : getTotalTimeAtNode(nodeIndex);
    }

    public TimeDelta getTotalTimeAtNode(int nodeIndex) {
        return getRoute().stream()
                .limit(nodeIndex + 1)
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimePoint getTimeAtNode(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        if (nodeIndex < 0)
            throw new IllegalArgumentException("Node not in route.");
        return getTimeAtNode(nodeIndex);
    }

    public TimePoint getTimeAtNode(int nodeIndex) {
        return startTime.plus(getTotalTimeAtNode(nodeIndex));
    }

    public LocationPoint getCurrentEnd() {
        if (end != null) return end.getPt();
        if (routeNodes.size() > 0)
            return routeNodes.get(routeNodes.size() - 1).getPt();
        return routeNodes.get(0).getPt();
    }

    @Override
    public int hashCode() {
        int result = routeNodes.hashCode();
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + startTime.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TravelRoute route = (TravelRoute) o;
        if (!routeNodes.equals(route.routeNodes)) return false;
        if (end != null ? !end.equals(route.end) : route.end != null) return false;
        return startTime.equals(route.startTime);

    }

    public TravelRoute clone() {
        TravelRoute route = new TravelRoute((StartPoint) routeNodes.get(0).getPt(), startTime);
        routeNodes.stream().filter(n -> !n.isStart()).forEach(route::addNode);
        if (end != null) route.setDestinationNode(end);
        if (!this.equals(route) && route.equals(this)) {
            LoggingUtils.logError("TravelRoute", "Inequal clone.");
        }
        return route;
    }

    public TravelRoute setDestinationNode(TravelRouteNode dest) {
        if (!dest.isDest()) {
            LoggingUtils.logError(getClass().getName() + "::setDestinationNode", "Node is not destination node.\nDest: " + dest.toString());
            throw new IllegalArgumentException("Node is not destination node.");
        }
        this.end = dest;
        return this;
    }

    @Override
    public String toString() {
        return "TravelRoute{" +
                "routeNodes=" + routeNodes.toString() +
                ", end=" + ((end != null) ? end.toString() : "NULL") +
                ", startTime=" + startTime.toString() +
                '}';
    }

    public TravelRoute cloneAtNode(int nodeIndex) {
        if (nodeIndex >= getRoute().size()) return clone();
        TravelRoute route = new TravelRoute((StartPoint) routeNodes.get(0)
                .getPt(), startTime);
        for (int i = 1; i < nodeIndex; i++) route.addNode(routeNodes.get(i));
        if (end != null && nodeIndex == getRoute().size()) route.setDestinationNode(end);
        return route;
    }

    public TravelRoute addNode(TravelRouteNode node) {
        if (node.isDest() || node.isStart()) {
            throw new IllegalArgumentException("Node is not station.\nNode: " + node.toString());
        }
        if (!isInRoute(node.getPt())) routeNodes.add(node);
        return this;
    }

    public boolean isInRoute(LocationPoint location) {
        return location != null && (
                Arrays.equals(location.getCoordinates(), getStart().getCoordinates())
                        || end != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())
                        || routeNodes.stream()
                        .map(TravelRouteNode::getPt)
                        .anyMatch(station -> Arrays.equals(station.getCoordinates(), location.getCoordinates()))
        );
    }

    public StartPoint getStart() {
        return (StartPoint) routeNodes.get(0).getPt();
    }

    public DestinationLocation getDestination() {
        return end != null ? (DestinationLocation) end.getPt() : null;
    }

}
