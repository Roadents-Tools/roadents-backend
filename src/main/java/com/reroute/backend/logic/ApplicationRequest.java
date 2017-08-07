package com.reroute.backend.logic;

import com.google.common.collect.Lists;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class ApplicationRequest {

    private List<StartPoint> starts;
    private List<DestinationLocation> ends;
    private TimePoint startTime;
    private TimeDelta maxDelta;
    private LocationType query;
    private Predicate<TravelRoute> resultsFilter;

    private String tag;
    private boolean isTest = false;


    public String getTag() {
        return tag;
    }

    public List<StartPoint> getStarts() {
        return Collections.unmodifiableList(starts);
    }

    public List<DestinationLocation> getEnds() {
        return Collections.unmodifiableList(ends);
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public TimeDelta getMaxDelta() {
        return maxDelta;
    }

    public LocationType getQuery() {
        return query;
    }

    public Predicate<TravelRoute> getResultsFilter() {
        return resultsFilter;
    }

    public boolean isTest() {
        return isTest;
    }

    @Override
    public String toString() {
        return "ApplicationRequest{" +
                "starts=" + starts +
                ", ends=" + ends +
                ", startTime=" + startTime +
                ", maxDelta=" + maxDelta +
                ", query=" + query +
                ", resultsFilter=" + resultsFilter +
                ", tag='" + tag + '\'' +
                ", isTest=" + isTest +
                '}';
    }

    public static class Builder {
        private ApplicationRequest rval;

        public Builder(String tag) {
            rval = new ApplicationRequest();
            rval.tag = tag;
        }

        public Builder withStartPoint(StartPoint point) {
            rval.starts = Lists.newArrayList(point);
            return this;
        }

        public Builder withStartPoints(StartPoint... points) {
            rval.starts = Lists.asList(null, points);
            return this;
        }

        public Builder withStartPoints(Collection<? extends StartPoint> points) {
            rval.starts = new ArrayList<>(points);
            return this;
        }

        public Builder withEndPoint(DestinationLocation routeEnd) {
            rval.ends = Lists.newArrayList(routeEnd);
            return this;
        }

        public Builder withEndPoints(DestinationLocation... routeEnds) {
            rval.ends = Lists.asList(null, routeEnds);
            return this;
        }

        public Builder withEndPoints(Collection<? extends DestinationLocation> routeEnds) {
            rval.ends = new ArrayList<>(routeEnds);
            return this;
        }

        public Builder withStartTime(TimePoint startTime) {
            rval.startTime = startTime;
            return this;
        }

        public Builder withMaxDelta(TimeDelta maxDelta) {
            rval.maxDelta = (maxDelta != null && maxDelta.getDeltaLong() > 0) ? maxDelta : TimeDelta.NULL;
            return this;
        }

        public Builder withQuery(LocationType type) {
            rval.query = type;
            return this;
        }

        public Builder withFilter(Predicate<TravelRoute> filter) {
            rval.resultsFilter = filter;
            return this;
        }

        public Builder isTest(boolean test) {
            rval.isTest = test;
            return this;
        }

        public ApplicationRequest build() {
            return rval;
        }
    }
}
