package org.tymit.projectdonut.model;

import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private List<TravelRouteNode> stationNodes;
    private TravelRouteNode start;
    private TravelRouteNode end;
    private Map<String, Object> ascCosts;
    private TimeModel startTime;

    public TravelRoute(StartPoint start, TimeModel startTime) {
        this.start = new TravelRouteNode.Builder().setPoint(start).build();
        stationNodes = new ArrayList<>();
        ascCosts = new ConcurrentHashMap<>();
        this.startTime = startTime;
    }

    public TimeModel getStartTime() {
        return startTime;
    }

    public boolean addNode(TravelRouteNode node) {
        if (node.isDest() || node.isStart()) {
            throw new IllegalArgumentException("Node is not station.\nNode: " + node.toString());
        }
        return !isInRoute(node.getPt()) && stationNodes.add(node);
    }

    public boolean isInRoute(LocationPoint location) {

        if (location == null) return false;

        if (Arrays.equals(location.getCoordinates(), getStart().getCoordinates())) {
            return true;
        }

        if (end != null && Arrays.equals(location.getCoordinates(), getDestination().getCoordinates())) {
            return true;
        }

        for (TravelRouteNode stationNode : stationNodes) {
            LocationPoint station = stationNode.getPt();
            if (Arrays.equals(station.getCoordinates(), location.getCoordinates())) {
                return true;
            }
        }

        return false;
    }

    public StartPoint getStart() {
        return (StartPoint) start.getPt();
    }

    public DestinationLocation getDestination() {
        if (end == null) return null;
        return (DestinationLocation) end.getPt();
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

    public Map<String, Object> getCosts() {
        return ascCosts;
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
        result = 31 * result + ascCosts.hashCode();
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
        if (!ascCosts.equals(route.ascCosts)) return false;
        return startTime.equals(route.startTime);

    }

    public TravelRoute clone() {
        TravelRoute route = new TravelRoute((StartPoint) start.getPt(), startTime);
        for (String tag : ascCosts.keySet()) route.putCost(tag, ascCosts.get(tag));
        stationNodes.forEach(route::addNode);
        if (end != null) route.setDestinationNode(end);
        if (!this.equals(route) && route.equals(this)) {
            LoggingUtils.logError("TravelRoute", "Inequal clone.");
        }
        return route;
    }

    public void setDestinationNode(TravelRouteNode dest) {
        if (!dest.isDest()) {
            LoggingUtils.logError(getClass().getName() + "::setDestinationNode", "Node is not destination node.\nDest: " + dest.toString());
            throw new IllegalArgumentException("Node is not destination node.");
        }
        this.end = dest;
    }

    @Override
    public String toString() {
        return "TravelRoute{" +
                "stationNodes=" + stationNodes.toString() +
                ", start=" + start.toString() +
                ", end=" + ((end != null) ? end.toString() : "NULL") +
                ", ascCosts=" + ascCosts.toString() +
                ", startTime=" + startTime.toString() +
                '}';
    }

    public void putCost(String tag, Object value) {
        ascCosts.put(tag, value);
    }


}
