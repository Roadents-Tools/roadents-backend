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
 * A route from a start location to some sort of end; it can either be a destination, in which case this route
 * is "finished" or some sort of intermediary point, in which case this route is "unfinished".
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private final List<TravelRouteNode> routeNodes;
    private final TimePoint startTime;
    private TravelRouteNode dest;

    /**
     * Constructs a new, empty TravelRoute.
     * @param start the location to start at
     * @param startTime the time to start at
     */
    public TravelRoute(StartPoint start, TimePoint startTime) {
        TravelRouteNode stNode = new TravelRouteNode.Builder()
                .setPoint(start)
                .build();
        routeNodes = new ArrayList<>();
        routeNodes.add(stNode);
        this.startTime = startTime;
    }

    /**
     * Copy constructor.
     *
     * @param base the route to copy
     */
    private TravelRoute(TravelRoute base) {
        this.routeNodes = new ArrayList<>(base.routeNodes);
        this.startTime = base.startTime;
        this.dest = base.dest;
    }

    /**
     * Gets the start time of the route.
     * @return the start time of the route.
     */
    public TimePoint getStartTime() {
        return startTime;
    }

    /**
     * Gets the current final location of the route, regardless of whether or not the route is finished.
     * @return the current end of the route
     */
    public LocationPoint getCurrentEnd() {
        if (dest != null) return dest.getPt();
        if (routeNodes.size() > 0) return routeNodes.get(routeNodes.size() - 1).getPt();
        return getStart();
    }

    /**
     * Gets the start location of this route.
     * @return the start location of this route
     */
    public StartPoint getStart() {
        return (StartPoint) routeNodes.get(0).getPt();
    }


    /**
     * Gets the total time spent walking in this route.
     * @return the total time walking in this route
     */
    public TimeDelta getWalkTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the nodes in this route.
     * @return the nodes in this route
     */
    public List<TravelRouteNode> getRoute() {
        List<TravelRouteNode> route = new ArrayList<>();
        route.addAll(routeNodes);
        if (dest != null) route.add(dest);
        return Collections.unmodifiableList(route);
    }

    /**
     * Gets the total walk time up to and including a given node.
     * @param node the final node to include
     * @return the total walk time up to and including the given node
     */
    public TimeDelta getWalkTimeAt(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWalkTimeAt(limit);
    }

    /**
     * Gets the walk time up to an including a given node.
     * @param nodeindex the index of the final node to include
     * @return the total walk time up to and including the given node
     */
    public TimeDelta getWalkTimeAt(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWalkTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the total walk distance travelled up to and including a given node.
     * @param node the final node to include
     * @return the total walk distance travelled up to and including the given node
     */
    public Distance getWalkDispAt(TravelRouteNode node) {
        int index = getRoute().indexOf(node);
        return getWalkDispAt(index);
    }

    /**
     * Gets the walk time up to an including a given node.
     * @param nodeIndex the index of the final node to include
     * @return the total walk time up to and including the given node
     */
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

    /**
     * Gets the total distance walked in this route.
     * @return the total distance walked in this route
     */
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

    /**
     * Gets the total time spent waiting for transit in this route.
     * @return the total time spent waiting in transit
     */
    public TimeDelta getWaitTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the wait time up to an including a given node.
     * @param node the final node to include
     * @return the total wait time up to and including the given node
     */
    public TimeDelta getWaitTimeAt(TravelRouteNode node) {
        int limit = getRoute().indexOf(node);
        return getWaitTimeAt(limit);
    }

    /**
     * Gets the wait time up to an including a given node.
     * @param nodeindex the index of the final node to include
     * @return the total wait time up to and including the given node
     */
    public TimeDelta getWaitTimeAt(int nodeindex) {
        return getRoute().stream()
                .limit(nodeindex + 1)
                .map(TravelRouteNode::getWaitTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the total time spent travelling in transit in this route.
     * @return the total time spent travelling in transit
     */
    public TimeDelta getTravelTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTravelTimeFromPrev)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the travel time up to an including a given node.
     * @param node the final node to include
     * @return the total travel time up to and including the given node
     */
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

    public TimeDelta getTimeAt(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        return nodeIndex < 0 ? TimeDelta.NULL : getTimeAt(nodeIndex);
    }

    /**
     * Gets the total distance travelled by this route.
     * @return the distance travelled by this route
     */
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

    /**
     * Gets the time at a given node.
     * @param node The node to get the time at
     * @return the time at the given node
     */
    public TimePoint getTimePointAt(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        if (nodeIndex < 0)
            throw new IllegalArgumentException("Node not in route.");
        return getTimePointAt(nodeIndex);
    }

    /**
     * Gets the time at a given node.
     * @param nodeIndex The node to get the time at
     * @return the time at the given node
     */
    public TimePoint getTimePointAt(int nodeIndex) {
        return startTime.plus(getTimeAt(nodeIndex));
    }

    /**
     * Gets the travel time up to an including a given node.
     *
     * @param nodeIndex the index of the final node to include
     * @return the total travel time up to and including the given node
     */
    public TimeDelta getTimeAt(int nodeIndex) {
        return getRoute().stream()
                .limit(nodeIndex + 1)
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Gets the time that the route is over.
     * @return the time the route is over
     */
    public TimePoint getEndTime() {
        return startTime.plus(getTime());
    }

    /**
     * Gets the total time in this route.
     * @return the total time in this route
     */
    public TimeDelta getTime() {
        return getRoute().parallelStream()
                .map(TravelRouteNode::getTotalTimeToArrive)
                .reduce(TimeDelta.NULL, TimeDelta::plus);
    }

    /**
     * Copies the route at a certain index.
     * @param nodeIndex the node to stop at
     * @return the copy of this route up to the given node
     */
    public TravelRoute copyAt(int nodeIndex) {
        if (nodeIndex >= getRoute().size()) return copy();
        TravelRoute route = new TravelRoute((StartPoint) routeNodes.get(0)
                .getPt(), startTime);
        for (int i = 1; i < nodeIndex; i++) route.addNode(routeNodes.get(i));
        if (dest != null && nodeIndex == getRoute().size()) route.setDestinationNode(dest);
        return route;
    }

    /**
     * Copies this travel route to a new object.
     * @return the copy
     */
    public TravelRoute copy() {
        TravelRoute route = new TravelRoute(this);
        if (!this.equals(route) && route.equals(this)) {
            LoggingUtils.logError("TravelRoute", "Inequal copy.");
        }
        return route;
    }

    /**
     * Sets the destination node to the node passed.
     * @param dest the node to use
     * @return this
     */
    public TravelRoute setDestinationNode(TravelRouteNode dest) {
        if (!dest.isDest()) {
            LoggingUtils.logError(getClass().getName() + "::setDestinationNode", "Node is not destination node.\nDest: " + dest
                    .toString());
            throw new IllegalArgumentException("Node is not destination node.");
        }
        this.dest = dest;
        return this;
    }

    /**
     * Adds a node to the given route.
     * @param node the node to add
     * @return this
     */
    public TravelRoute addNode(TravelRouteNode node) {
        if (node.isStart()) {
            throw new IllegalArgumentException("Cannot add another start node. Node:" + node.toString());
        }
        if (!isInRoute(node.getPt())) routeNodes.add(node);
        return this;
    }

    /**
     * Checks whether a location is in the route.
     * @param location the location to check
     * @return whether the location is in the route
     */
    public boolean isInRoute(LocationPoint location) {
        return location != null && (
                Arrays.equals(location.getCoordinates(), getStart().getCoordinates())
                        || dest != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())
                        || routeNodes.stream()
                        .map(TravelRouteNode::getPt)
                        .anyMatch(station -> Arrays.equals(station.getCoordinates(), location.getCoordinates()))
        );
    }

    /**
     * Gets the destination of this route.
     * @return the destination if this route is finished or null otherwise.
     */
    public DestinationLocation getDestination() {
        return dest != null ? (DestinationLocation) dest.getPt() : null;
    }

    /**
     * Copies the route, applying a transformation.
     * @param modify the transformation to apply to the copy
     * @return a copy of this route with the transformation applied
     */
    public TravelRoute copyWith(Function<TravelRoute, TravelRoute> modify) {
        return modify.apply(copy());
    }

    /**
     * Copies the route, applying a transformation.
     * @param modify the transformation to apply to the copy
     * @return a copy of this route with the transformation applied
     */
    public TravelRoute copyWith(Consumer<TravelRoute> modify) {
        TravelRoute rval = copy();
        modify.accept(rval);
        return rval;
    }

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
