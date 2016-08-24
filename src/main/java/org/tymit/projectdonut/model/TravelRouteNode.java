package org.tymit.projectdonut.model;

import org.tymit.projectdonut.utils.LoggingUtils;

/**
 * Created by ilan on 8/22/16.
 */
public class TravelRouteNode {

    private LocationPoint pt;
    private long walkTimeFromPrev;
    private long waitTimeFromPrev;
    private long travelTimeFromPrev;

    private TravelRouteNode() {
        pt = null;
        waitTimeFromPrev = -1;
        walkTimeFromPrev = -1;
        travelTimeFromPrev = -1;
    }

    public boolean arrivesByTransportation() {
        return !isStart() && !arrivesByFoot();
    }

    public boolean isStart() {
        return pt instanceof StartPoint;
    }

    public boolean arrivesByFoot() {
        return !isStart() && waitTimeFromPrev < 0 && travelTimeFromPrev < 0;
    }

    public boolean isDest() {
        return pt instanceof DestinationLocation;
    }

    public long getTotalTimeToArrive() {
        if (isStart()) return -1;
        long totalTime = 0;
        if (waitTimeFromPrev > 0) totalTime += waitTimeFromPrev;
        if (travelTimeFromPrev > 0) totalTime += travelTimeFromPrev;
        if (walkTimeFromPrev > 0) totalTime += walkTimeFromPrev;
        return totalTime;
    }

    public LocationPoint getPt() {
        return pt;
    }

    public long getWalkTimeFromPrev() {
        return walkTimeFromPrev;
    }

    public long getWaitTimeFromPrev() {
        return waitTimeFromPrev;
    }

    public long getTravelTimeFromPrev() {
        return travelTimeFromPrev;
    }

    @Override
    public int hashCode() {
        int result = pt.hashCode();
        result = 31 * result + (int) (walkTimeFromPrev ^ (walkTimeFromPrev >>> 32));
        result = 31 * result + (int) (waitTimeFromPrev ^ (waitTimeFromPrev >>> 32));
        result = 31 * result + (int) (travelTimeFromPrev ^ (travelTimeFromPrev >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TravelRouteNode that = (TravelRouteNode) o;

        if (walkTimeFromPrev != that.walkTimeFromPrev) return false;
        if (waitTimeFromPrev != that.waitTimeFromPrev) return false;
        if (travelTimeFromPrev != that.travelTimeFromPrev) return false;
        return pt.equals(that.pt);

    }

    @Override
    public String toString() {
        return "TravelRouteNode{" +
                "pt=" + pt.toString() +
                ", walkTimeFromPrev=" + walkTimeFromPrev +
                ", waitTimeFromPrev=" + waitTimeFromPrev +
                ", travelTimeFromPrev=" + travelTimeFromPrev +
                '}';
    }

    public static class Builder {
        private TravelRouteNode output;

        public Builder() {
            output = new TravelRouteNode();
        }

        public Builder setPoint(LocationPoint pt) {
            output.pt = pt;
            return this;
        }

        public Builder setWalkTime(long walkTime) {
            output.walkTimeFromPrev = walkTime;
            return this;
        }

        public Builder setWaitTime(long waitTime) {
            output.waitTimeFromPrev = waitTime;
            return this;
        }

        public Builder setTravelTime(long travelTime) {
            output.travelTimeFromPrev = travelTime;
            return this;
        }

        public TravelRouteNode build() {
            if (output.pt == null) {
                LoggingUtils.logError("TravelRouteNode.Builder", "Point not set.");
                throw new IllegalStateException("Method Builder::setPoint must be called before Builder::build.");
            }
            return output;
        }
    }
}
