package com.reroute.backend.model.routing;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private final List<TravelRouteNode> routeNodes;
    private final TimePoint startTime;
    private TravelRouteNode dest;

    /**
     * Constructors
     **/

    public TravelRoute(StartPoint start, TimePoint startTime) {
        TravelRouteNode stNode = new TravelRouteNode.Builder()
                .setPoint(start)
                .build();
        routeNodes = new ArrayList<>();
        routeNodes.add(stNode);
        this.startTime = startTime;
    }

    private TravelRoute(TravelRoute base) {
        this.routeNodes = new ArrayList<>(base.routeNodes);
        this.startTime = base.startTime;
        this.dest = base.dest;
    }

    /**
     * Basic, low logic accessors
     **/


    public TimePoint getStartTime() {
        return startTime;
    }

    public LocationPoint getCurrentEnd() {
        if (dest != null) return dest.getPt();
        if (routeNodes.size() > 0) return routeNodes.get(routeNodes.size() - 1).getPt();
        return getStart();
    }

    public StartPoint getStart() {
        return (StartPoint) routeNodes.get(0).getPt();
    }

    /**
     * Higher-logic walk-based calculation methods
     **/

    public TimeDelta getWalkTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public List<TravelRouteNode> getRoute() {
        List<TravelRouteNode> route = new ArrayList<>();
        route.addAll(routeNodes);
        if (dest != null) route.add(dest);
        return Collections.unmodifiableList(route);
    }

    public TimeDelta getWalkTimeAt(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWalkTimeAt(limit);
    }

    public TimeDelta getWalkTimeAt(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public Distance getWalkDispAt(TravelRouteNode node) {
        int index = getRoute().indexOf(node);
        return getWalkDispAt(index);
    }

    public Distance getWalkDispAt(int nodeIndex) {
        if (nodeIndex >= getRoute().size() - 1) return getWalkDisp();
        Distance rval = Distance.NULL;
        for (int i = 0; i <= nodeIndex; i++) {
            TravelRouteNode current = getRoute().get(i);
            if (!current.arrivesByFoot()) continue;
            TravelRouteNode prev = getRoute().get(i - 1);
            Distance toAdd = LocationUtils.distanceBetween(current.getPt(), prev.getPt());
            rval = rval.plus(toAdd);
        }
        return rval;
    }

    public Distance getWalkDisp() {
        Distance rval = Distance.NULL;
        for (int i = 0; i < getRoute().size(); i++) {
            TravelRouteNode current = getRoute().get(i);
            if (!current.arrivesByFoot()) continue;
            TravelRouteNode prev = getRoute().get(i - 1);
            Distance toAdd = LocationUtils.distanceBetween(current.getPt(), prev.getPt());
            rval = rval.plus(toAdd);
        }
        return rval;
    }

    /** Higher-logic wait-based calculation methods **/

    public TimeDelta getWaitTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getWaitTimeAt(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWaitTimeAt(limit);
    }

    public TimeDelta getWaitTimeAt(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Higher-logic travel-based calculation methods
     **/


    public TimeDelta getTravelTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TimeDelta getTravelTimeAt(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getTravelTimeAt(limit);
    }

    public TimeDelta getTravelTimeAt(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Higher-logic general calculation algorithms
     **/


    public TimeDelta getTimeAt(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        return nodeIndex < 0 ? TimeDelta.NULL : getTimeAt(nodeIndex);
    }

    public TimeDelta getTimeAt(int nodeIndex) {
        return getRoute().stream()
                .limit(nodeIndex + 1)
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public Distance getDisp() {
        Distance rval = Distance.NULL;
        for (int i = 1; i < getRoute().size(); i++) {
            TravelRouteNode cur = getRoute().get(i);
            TravelRouteNode prev = getRoute().get(i - 1);
            Distance toAdd = LocationUtils.distanceBetween(cur.getPt(), prev.getPt());
            rval = rval.plus(toAdd);
        }
        return rval;
    }

    public TimePoint getTimePointAt(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        if (nodeIndex < 0)
            throw new IllegalArgumentException("Node not in route.");
        return getTimePointAt(nodeIndex);
    }

    public TimePoint getTimePointAt(int nodeIndex) {
        return startTime.plus(getTimeAt(nodeIndex));
    }

    public TimePoint getEndTime() {
        return startTime.plus(getTime());
    }

    public TimeDelta getTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    public TravelRoute copyAt(int nodeIndex) {
        if (nodeIndex >= getRoute().size()) return copy();
        TravelRoute route = new TravelRoute((StartPoint) routeNodes.get(0)
                .getPt(), startTime);
        for (int i = 1; i < nodeIndex; i++) route.addNode(routeNodes.get(i));
        if (dest != null && nodeIndex == getRoute().size()) route.setDestinationNode(dest);
        return route;
    }

    /**
     * Modifiers and Immutable-Styled "modifiers"
     **/

    public TravelRoute copy() {
        TravelRoute route = new TravelRoute(this);
        if (!this.equals(route) && route.equals(this)) {
            LoggingUtils.logError("TravelRoute", "Inequal copy.");
        }
        return route;
    }

    public TravelRoute setDestinationNode(TravelRouteNode dest) {
        if (!dest.isDest()) {
            LoggingUtils.logError(getClass().getName() + "::setDestinationNode", "Node is not destination node.\nDest: " + dest
                    .toString());
            throw new IllegalArgumentException("Node is not destination node.");
        }
        this.dest = dest;
        return this;
    }

    public TravelRoute addNode(TravelRouteNode node) {
        if (node.isStart()) {
            throw new IllegalArgumentException("Cannot add another start node. Node:" + node.toString());
        }
        if (!isInRoute(node.getPt())) routeNodes.add(node);
        return this;
    }

    public boolean isInRoute(LocationPoint location) {
        return location != null && (
                Arrays.equals(location.getCoordinates(), getStart().getCoordinates())
                        || dest != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())
                        || routeNodes.stream()
                        .map(TravelRouteNode::getPt)
                        .anyMatch(station -> Arrays.equals(station.getCoordinates(), location.getCoordinates()))
        );
    }

    public DestinationLocation getDestination() {
        return dest != null ? (DestinationLocation) dest.getPt() : null;
    }

    public TravelRoute copyWith(Function<TravelRoute, TravelRoute> modify) {
        return modify.apply(copy());
    }

    public TravelRoute copyWith(Consumer<TravelRoute> modify) {
        TravelRoute rval = copy();
        modify.accept(rval);
        return rval;
    }

    /**
     * Java boilerplate
     **/

    @Override
    public int hashCode() {
        int result = routeNodes.hashCode();
        result = 31 * result + (dest != null ? dest.hashCode() : 0);
        result = 31 * result + startTime.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TravelRoute route = (TravelRoute) o;
        if (!routeNodes.equals(route.routeNodes)) return false;
        if (dest != null ? !dest.equals(route.dest) : route.dest != null) return false;
        return startTime.equals(route.startTime);

    }


    @Override
    public String toString() {
        return "TravelRoute{" +
                "routeNodes=" + routeNodes.toString() +
                ", dest=" + ((dest != null) ? dest.toString() : "NULL") +
                ", startTime=" + startTime.toString() +
                '}';
    }
}
