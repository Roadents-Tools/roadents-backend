package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private final List<TravelRouteNode> routeNodes;
    private final TimePoint startTime;
    private TravelRouteNode end;

    public TravelRoute(StartPoint start, TimePoint startTime) {
        TravelRouteNode stNode = new TravelRouteNode.Builder()
                .setPoint(start)
                .build();
        routeNodes = new ArrayList<>();
        routeNodes.add(stNode);
        this.startTime = startTime;
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public TimeDelta getTotalWalkTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getTotalTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getWalkTimeAtNode(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWalkTimeAtNodeIndex(limit);
    }

    public TimeDelta getWalkTimeAtNodeIndex(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getTotalWaitTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getWaitTimeAtNode(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWaitTimeAtNodeIndex(limit);
    }

    public TimeDelta getWaitTimeAtNodeIndex(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getTotalTravelTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getTravelTimeAtNode(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getTravelTimeAtNodeIndex(limit);
    }

    public TimeDelta getTravelTimeAtNodeIndex(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
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

    public TimePoint getEndTime() {
        return startTime.plus(getTotalTime());
    }

    public List<TravelRouteNode> getRoute() {
        List<TravelRouteNode> route = new ArrayList<>();
        route.addAll(routeNodes);
        if (end != null) route.add(end);
        return Collections.unmodifiableList(route);
    }

    public LocationPoint getCurrentEnd() {
        if (end != null) return end.getPt();
        if (routeNodes.size() > 0) return routeNodes.get(routeNodes.size() - 1).getPt();
        return getStart();
    }

    public StartPoint getStart() {
        return (StartPoint) routeNodes.get(0).getPt();
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
            LoggingUtils.logError(getClass().getName() + "::setDestinationNode", "Node is not destination node.\nDest: " + dest
                    .toString());
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

    public boolean isInRoute(LocationPoint location) {
        return location != null && (
                Arrays.equals(location.getCoordinates(), getStart().getCoordinates())
                        || end != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())
                        || routeNodes.stream()
                        .map(TravelRouteNode::getPt)
                        .anyMatch(station -> Arrays.equals(station.getCoordinates(), location.getCoordinates()))
        );
    }

    public TravelRoute addNode(TravelRouteNode node) {
        if (node.isStart()) {
            throw new IllegalArgumentException("Cannot add another start node. Node:" + node.toString());
        }
        if (!isInRoute(node.getPt())) routeNodes.add(node);
        return this;
    }

    public DestinationLocation getDestination() {
        return end != null ? (DestinationLocation) end.getPt() : null;
    }

}
