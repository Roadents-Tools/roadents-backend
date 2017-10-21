package com.reroute.backend.logic.utils;


import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;

import java.util.function.Predicate;

public class StationRoutesBuildRequest {
    private StartPoint initialPoint;
    private TimePoint startTime;
    private TimeDelta maxDelta;
    private Predicate<TravelRoute> layerFilter = a -> true;
    private Predicate<TravelRoute> endFilter = a -> true;
    private int layerLimit = Integer.MAX_VALUE;
    private int finalLimit = Integer.MAX_VALUE;

    public StationRoutesBuildRequest(StartPoint startPoint, TimePoint startTime, TimeDelta maxDelta) {
        this.startTime = startTime;
        this.maxDelta = maxDelta;
        this.initialPoint = startPoint;
    }

    public StationRoutesBuildRequest withInitialPoint(StartPoint initialPoint) {
        StationRoutesBuildRequest rval = copy();
        rval.initialPoint = initialPoint;
        return rval;
    }

    private StationRoutesBuildRequest copy() {
        StationRoutesBuildRequest rval = new StationRoutesBuildRequest(initialPoint, startTime, maxDelta);
        rval.layerFilter = layerFilter;
        rval.endFilter = endFilter;
        rval.layerLimit = layerLimit;
        rval.finalLimit = finalLimit;
        return rval;
    }

    public StationRoutesBuildRequest withStartTime(TimePoint startTime) {
        StationRoutesBuildRequest rval = copy();
        rval.startTime = startTime;
        return rval;
    }

    public StationRoutesBuildRequest withMaxDelta(TimeDelta maxDelta) {
        StationRoutesBuildRequest rval = copy();
        rval.maxDelta = maxDelta;
        return rval;
    }

    public StationRoutesBuildRequest withLayerFilter(Predicate<TravelRoute> layerFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.layerFilter = layerFilter;
        return rval;
    }

    public StationRoutesBuildRequest andLayerFilter(Predicate<TravelRoute> layerFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.layerFilter = rval.layerFilter.and(layerFilter);
        return rval;
    }

    public StationRoutesBuildRequest orLayerFilter(Predicate<TravelRoute> layerFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.layerFilter = rval.layerFilter.or(layerFilter);
        return rval;
    }

    public StationRoutesBuildRequest withEndFilter(Predicate<TravelRoute> endFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.endFilter = endFilter;
        return rval;
    }

    public StationRoutesBuildRequest andEndFilter(Predicate<TravelRoute> endFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.endFilter = rval.endFilter.and(endFilter);
        return rval;
    }

    public StationRoutesBuildRequest orEndFilter(Predicate<TravelRoute> endFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.endFilter = rval.endFilter.or(endFilter);
        return rval;
    }

    public StationRoutesBuildRequest withLayerLimit(int layerLimit) {
        StationRoutesBuildRequest rval = copy();
        rval.layerLimit = layerLimit;
        return rval;
    }

    public StationRoutesBuildRequest withFinalLimit(int finalLimit) {
        StationRoutesBuildRequest rval = copy();
        rval.finalLimit = finalLimit;
        return rval;
    }

    public StartPoint getInitialPoint() {
        return initialPoint;
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public TimeDelta getMaxDelta() {
        return maxDelta;
    }

    public Predicate<TravelRoute> getLayerFilter() {
        return layerFilter;
    }

    public Predicate<TravelRoute> getEndFilter() {
        return endFilter;
    }

    public int getLayerLimit() {
        return layerLimit;
    }

    public int getFinalLimit() {
        return finalLimit;
    }
}


