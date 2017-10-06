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
        rval.setInitialPoint(initialPoint);
        return rval;
    }

    public StationRoutesBuildRequest copy() {
        StationRoutesBuildRequest rval = new StationRoutesBuildRequest(initialPoint, startTime, maxDelta);
        rval.layerFilter = layerFilter;
        rval.endFilter = endFilter;
        rval.layerLimit = layerLimit;
        rval.finalLimit = finalLimit;
        return rval;
    }

    public StationRoutesBuildRequest withStartTime(TimePoint startTime) {
        StationRoutesBuildRequest rval = copy();
        rval.setStartTime(startTime);
        return rval;
    }

    public StationRoutesBuildRequest withLayerFilter(Predicate<TravelRoute> layerFilter) {
        StationRoutesBuildRequest rval = copy();
        rval.layerFilter = layerFilter;
        return rval;
    }

    public StationRoutesBuildRequest withLayerLimit(int layerLimit) {
        StationRoutesBuildRequest rval = copy();
        rval.layerLimit = layerLimit;
        return rval;
    }

    public StartPoint getInitialPoint() {
        return initialPoint;
    }

    public StationRoutesBuildRequest setInitialPoint(StartPoint initialPoint) {
        this.initialPoint = initialPoint;
        return this;
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public StationRoutesBuildRequest setStartTime(TimePoint startTime) {
        this.startTime = startTime;
        return this;
    }

    public TimeDelta getMaxDelta() {
        return maxDelta;
    }

    public StationRoutesBuildRequest setMaxDelta(TimeDelta maxDelta) {
        this.maxDelta = maxDelta;
        return this;
    }

    public Predicate<TravelRoute> getLayerFilter() {
        return layerFilter;
    }

    public StationRoutesBuildRequest setLayerFilter(Predicate<TravelRoute> layerFilter) {
        this.layerFilter = layerFilter;
        return this;
    }

    public Predicate<TravelRoute> getEndFilter() {
        return endFilter;
    }

    public StationRoutesBuildRequest setEndFilter(Predicate<TravelRoute> endFilter) {
        this.endFilter = endFilter;
        return this;
    }

    public int getLayerLimit() {
        return layerLimit;
    }

    public StationRoutesBuildRequest setLayerLimit(int layerLimit) {
        this.layerLimit = layerLimit;
        return this;
    }

    public int getFinalLimit() {
        return finalLimit;
    }

    public StationRoutesBuildRequest setFinalLimit(int finalLimit) {
        this.finalLimit = finalLimit;
        return this;
    }
}


