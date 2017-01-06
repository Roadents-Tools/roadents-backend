package org.tymit.projectdonut.model;

import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private final List<TravelRouteNode> stationNodes;
    private final TravelRouteNode start;
    private final TimeModel startTime;
    private TravelRouteNode end;

    public TravelRoute(StartPoint start, TimeModel startTime) {
        this.start = new TravelRouteNode.Builder().setPoint(start).build();
        stationNodes = new ArrayList<>();
        this.startTime = startTime;
    }

    public TimeModel getStartTime() {
        return startTime;
    }

    public long getTotalTime() {
        return getRoute().parallelStream().mapToLong(TravelRouteNode::getTotalTimeToArrive).sum();
    }

    public List<TravelRouteNode> getRoute() {
        List<TravelRouteNode> route = new ArrayList<>();
        route.add(start);
        route.addAll(stationNodes);
        if (end != null) route.add(end);
        return route;
    }

    public long getTotalTimeAtNode(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        return nodeIndex < 0 ? -1 : getTotalTimeAtNode(nodeIndex);
    }

    public long getTotalTimeAtNode(int nodeIndex) {
        return getRoute().stream().limit(nodeIndex + 1).mapToLong(TravelRouteNode::getTotalTimeToArrive).sum();
    }

    public TimeModel getTimeAtNode(TravelRouteNode node) {
        int nodeIndex = getRoute().indexOf(node);
        return nodeIndex < 0 ? TimeModel.empty() : getTimeAtNode(nodeIndex);
    }

    public TimeModel getTimeAtNode(int nodeIndex) {
        return startTime.addUnixTime(getTotalTimeAtNode(nodeIndex));
    }

    public LocationPoint getCurrentEnd() {
        if (end != null) return end.getPt();
        if (stationNodes.size() > 0) return stationNodes.get(stationNodes.size() - 1).getPt();
        return start.getPt();
    }

    @Override
    public int hashCode() {
        int result = stationNodes.hashCode();
        result = 31 * result + start.hashCode();
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + startTime.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TravelRoute route = (TravelRoute) o;
        if (!stationNodes.equals(route.stationNodes)) return false;
        if (!start.equals(route.start)) return false;
        if (end != null ? !end.equals(route.end) : route.end != null) return false;
        return startTime.equals(route.startTime);

    }

    public TravelRoute clone() {
        TravelRoute route = new TravelRoute((StartPoint) start.getPt(), startTime);
        stationNodes.forEach(route::addNode);
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
                "stationNodes=" + stationNodes.toString() +
                ", start=" + start.toString() +
                ", end=" + ((end != null) ? end.toString() : "NULL") +
                ", startTime=" + startTime.toString() +
                '}';
    }

    public TravelRoute cloneAtNode(int nodeIndex) {
        if (nodeIndex >= getRoute().size()) return clone();
        TravelRoute route = new TravelRoute((StartPoint) start.getPt(), startTime);
        for (int i = 0; i < nodeIndex; i++) route.addNode(stationNodes.get(i));
        if (end != null && nodeIndex == getRoute().size()) route.setDestinationNode(end);
        return route;
    }

    public TravelRoute addNode(TravelRouteNode node) {
        if (node.isDest() || node.isStart()) {
            throw new IllegalArgumentException("Node is not station.\nNode: " + node.toString());
        }
        if (!isInRoute(node.getPt())) stationNodes.add(node);
        return this;
    }

    public boolean isInRoute(LocationPoint location) {
        return location != null && (
                Arrays.equals(location.getCoordinates(), getStart().getCoordinates())
                        || end != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())
                        || stationNodes.stream()
                        .map(TravelRouteNode::getPt)
                        .anyMatch(station -> Arrays.equals(station.getCoordinates(), location.getCoordinates()))
        );
    }

    public StartPoint getStart() {
        return (StartPoint) start.getPt();
    }

    public DestinationLocation getDestination() {
        return end != null ? (DestinationLocation) end.getPt() : null;
    }

}
