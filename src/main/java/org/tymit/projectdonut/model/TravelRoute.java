package org.tymit.projectdonut.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        ascCosts = new HashMap<>();
    }

    public List<LocationPoint> getRoute() {
        List<LocationPoint> route = new ArrayList<>();
        route.add(start);
        route.addAll(stations);
        if (end != null) route.add(end);
        return route;
    }

    public boolean isInRoute(LocationPoint location) {
        return location.equals(start) || location.equals(end) || stations.contains(location);
    }

    public boolean addStation(TransStation station) {
        if (end != null || stations.contains(station)) return false;
        return stations.add(station);
    }

    public DestinationLocation getDestination() {
        return end;
    }

    public void setDestination(DestinationLocation dest) {
        this.end = dest;
    }

    public void putCost(String tag, Object value) {
        ascCosts.put(tag, value);
    }

    public Map<String, Object> getCosts() {
        return ascCosts;
    }


}
