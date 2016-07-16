package org.tymit.projectdonut.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/8/16.
 */
public class TravelRoute {

    private List<TransStation> stations;
    private LocationPoint start;
    private DestinationLocation end;
    private Map<String, Object> ascCosts;
    private TimeModel startTime;

    public TravelRoute(LocationPoint start, TimeModel startTime) {
        this.start = start;
        stations = new ArrayList<>();
        ascCosts = new ConcurrentHashMap<>();
        this.startTime = startTime;
    }

    public TimeModel getStartTime() {
        return startTime;
    }

    public List<LocationPoint> getRoute() {
        List<LocationPoint> route = new ArrayList<>();
        route.add(start);
        route.addAll(stations);
        if (end != null) route.add(end);
        return route;
    }

    public boolean addStation(TransStation station) {
        if (isInRoute(station)) return false;
        return stations.add(station);
    }

    public boolean isInRoute(LocationPoint location) {
        return location.equals(start) || location.equals(end) || stations.contains(location);
    }

    public DestinationLocation getDestination() {
        return end;
    }

    public void setDestination(DestinationLocation dest) {
        this.end = dest;
    }

    public Map<String, Object> getCosts() {
        return ascCosts;
    }

    public LocationPoint getCurrentEnd() {
        if (end != null) return end;
        if (stations.size() > 0) return stations.get(stations.size() - 1);
        return start;
    }

    @Override
    public int hashCode() {
        int result = stations.hashCode();
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

        if (!stations.equals(route.stations)) return false;
        if (!start.equals(route.start)) return false;
        if (end != null ? !end.equals(route.end) : route.end != null) return false;
        return startTime.equals(route.startTime);

    }

    public TravelRoute clone() {
        TravelRoute route = new TravelRoute(start, startTime);
        for (String tag : ascCosts.keySet()) route.putCost(tag, ascCosts.get(tag));
        stations.forEach(route::addStation);
        if (end != null) route.setDestination(end);
        if (!this.equals(route) && route.equals(this)) throw new Error("INEQUAL CLONE");
        return route;
    }

    public void putCost(String tag, Object value) {
        ascCosts.put(tag, value);
    }
}
