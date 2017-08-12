package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.utils.LoggingUtils;

/**
 * Created by ilan on 8/22/16.
 */
public class TravelRouteNode {

    private LocationPoint pt;
    private TimeDelta walkTimeFromPrev;
    private TimeDelta waitTimeFromPrev;
    private TimeDelta travelTimeFromPrev;

    private TravelRouteNode(LocationPoint pt, TimeDelta walkTime, TimeDelta waitTime, TimeDelta travelTime) {
        this.pt = pt;
        this.waitTimeFromPrev = waitTime;
        this.walkTimeFromPrev = walkTime;
        this.travelTimeFromPrev = travelTime;
    }

    public boolean arrivesByTransportation() {
        return !isStart() && !arrivesByFoot();
    }

    public boolean isStart() {
        return pt instanceof StartPoint;
    }

    public boolean arrivesByFoot() {
        return !isStart() && waitTimeFromPrev == TimeDelta.NULL && travelTimeFromPrev == TimeDelta.NULL;
    }

    public boolean isDest() {
        return pt instanceof DestinationLocation;
    }

    public TimeDelta getTotalTimeToArrive() {
        return TimeDelta.NULL
                .plus(waitTimeFromPrev)
                .plus(walkTimeFromPrev)
                .plus(travelTimeFromPrev);
    }

    @Override
    public int hashCode() {
        int result = getPt().hashCode();
        result = 31 * result + getWalkTimeFromPrev().hashCode();
        result = 31 * result + getWaitTimeFromPrev().hashCode();
        result = 31 * result + getTravelTimeFromPrev().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TravelRouteNode that = (TravelRouteNode) o;

        if (!getPt().equals(that.getPt())) return false;
        if (!getWalkTimeFromPrev().equals(that.getWalkTimeFromPrev()))
            return false;
        if (!getWaitTimeFromPrev().equals(that.getWaitTimeFromPrev()))
            return false;
        return getTravelTimeFromPrev().equals(that.getTravelTimeFromPrev());
    }

    @Override
    public String toString() {
        return "TravelRouteNode{" +
                "pt=" + pt +
                ", walkTimeFromPrev=" + walkTimeFromPrev +
                ", waitTimeFromPrev=" + waitTimeFromPrev +
                ", travelTimeFromPrev=" + travelTimeFromPrev +
                '}';
    }

    public LocationPoint getPt() {
        return pt;
    }

    public TimeDelta getWalkTimeFromPrev() {
        return walkTimeFromPrev;
    }

    public TimeDelta getWaitTimeFromPrev() {
        return waitTimeFromPrev;
    }

    public TimeDelta getTravelTimeFromPrev() {
        return travelTimeFromPrev;
    }

    public static class Builder {
        private TimeDelta walkTime = TimeDelta.NULL;
        private TimeDelta waitTime = TimeDelta.NULL;
        private TimeDelta travelTime = TimeDelta.NULL;
        private LocationPoint pt = null;

        public Builder() {
        }

        public Builder setPoint(LocationPoint pt) {
            this.pt = pt;
            return this;
        }

        public Builder setWalkTime(long walkTime) {
            this.walkTime = (walkTime != 0) ? new TimeDelta(walkTime) : TimeDelta.NULL;
            return this;
        }

        public Builder setWaitTime(long waitTime) {
            this.waitTime = (waitTime != 0) ? new TimeDelta(waitTime) : TimeDelta.NULL;
            return this;
        }

        public Builder setTravelTime(long travelTime) {
            this.travelTime = (travelTime != 0) ? new TimeDelta(travelTime) : TimeDelta.NULL;
            return this;
        }

        public TravelRouteNode build() {
            if (this.pt == null) {
                LoggingUtils.logError("TravelRouteNode.Builder", "Point not set.");
                throw new IllegalStateException("Method Builder::setPoint must be called before Builder::build.");
            }
            return new TravelRouteNode(pt, walkTime, waitTime, travelTime);
        }
    }
}
