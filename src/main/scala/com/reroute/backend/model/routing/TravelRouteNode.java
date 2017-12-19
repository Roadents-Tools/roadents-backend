package com.reroute.backend.model.routing;

import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.utils.LoggingUtils;

/**
 * A single stopping point in a route.
 * Created by ilan on 8/22/16.
 */
public class TravelRouteNode {

    private final LocationPoint pt;
    private final TimeDelta walkTimeFromPrev;
    private final TimeDelta waitTimeFromPrev;
    private final TimeDelta travelTimeFromPrev;

    /**
     * Creates a new node.
     * NOTE: This never called directly, only through TravelRouteNode.Builder::build.
     *
     * @param pt         the point that this node represents arrival at
     * @param walkTime   the time to walk to this node
     * @param waitTime   the time spent waiting for transit to get to this node
     * @param travelTime the time spent on transit to get to this node
     */
    private TravelRouteNode(LocationPoint pt, TimeDelta walkTime, TimeDelta waitTime, TimeDelta travelTime) {
        this.pt = pt;
        this.waitTimeFromPrev = waitTime;
        this.walkTimeFromPrev = walkTime;
        this.travelTimeFromPrev = travelTime;
    }

    /**
     * Checks whether this node is a "transit node," or a node in which we arrive via public transit.
     * @return whether this node is a transit node
     */
    public boolean arrivesByTransportation() {
        return !isStart() && !arrivesByFoot();
    }

    /**
     * Checks whether this node is a "start node," or a node that begins a route without any travel beforehand.
     * @return whether this node is a start node
     */
    public boolean isStart() {
        return pt instanceof StartPoint && getTotalTimeToArrive() == TimeDelta.NULL;
    }

    /**
     * Returns the total time this node takes.
     * @return the total time this node takes
     */
    public TimeDelta getTotalTimeToArrive() {
        return TimeDelta.NULL
                .plus(waitTimeFromPrev)
                .plus(walkTimeFromPrev)
                .plus(travelTimeFromPrev);
    }

    /**
     * Checks whether this node is a "walk node," or a node in which we arrive via walking.
     *
     * @return whether this node is a walk node
     */
    public boolean arrivesByFoot() {
        return !isStart() && waitTimeFromPrev == TimeDelta.NULL && travelTimeFromPrev == TimeDelta.NULL;
    }

    /**
     * Checks whether this node is a "destination node," or a node that does not arrive at a location we are using to
     * keep going and is not a start node. All finished routes end in a destination node, though in theory a destination
     * node can also appear in the middle of a route if a pit stop is made. This value is independent on whether this
     * node is a walk node or a transit node.
     *
     * @return whether this node is a destination node
     */
    public boolean isDest() {
        return pt instanceof DestinationLocation;
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
        return getWalkTimeFromPrev().equals(that.getWalkTimeFromPrev()) &&
                getWaitTimeFromPrev().equals(that.getWaitTimeFromPrev()) &&
                getTravelTimeFromPrev().equals(that.getTravelTimeFromPrev());
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

    /**
     * Gets the point that this node arrives at.
     * @return the point this node arrives at
     */
    public LocationPoint getPt() {
        return pt;
    }

    /**
     * Gets the walk time from the previous location.
     * @return the walk time from the previous location
     */
    public TimeDelta getWalkTimeFromPrev() {
        return walkTimeFromPrev;
    }

    /**
     * Gets the wait time from the previous location.
     * @return the wait time from the previous location
     */
    public TimeDelta getWaitTimeFromPrev() {
        return waitTimeFromPrev;
    }

    /**
     * Gets the travel time from the previous location.
     * @return the travel time from the previous location
     */
    public TimeDelta getTravelTimeFromPrev() {
        return travelTimeFromPrev;
    }

    /**
     * A class for building TravelRouteNodes.
     */
    public static class Builder {
        private TimeDelta walkTime = TimeDelta.NULL;
        private TimeDelta waitTime = TimeDelta.NULL;
        private TimeDelta travelTime = TimeDelta.NULL;
        private LocationPoint pt = null;

        public Builder() {
        }

        /**
         * Sets the point that newly built nodes will arrive at.
         * @param pt the point newly built nodes will arrive at
         * @return this
         */
        public Builder setPoint(LocationPoint pt) {
            this.pt = pt;
            return this;
        }

        /**
         * Sets the walk time of newly built nodes.
         * @param walkTime the walk time of newly built nodes
         * @return this
         */
        public Builder setWalkTime(long walkTime) {
            this.walkTime = (walkTime != 0) ? new TimeDelta(walkTime) : TimeDelta.NULL;
            return this;
        }

        /**
         * Sets the wait time of newly built nodes.
         * @param waitTime the wait time of newly built nodes
         * @return this
         */
        public Builder setWaitTime(long waitTime) {
            this.waitTime = (waitTime != 0) ? new TimeDelta(waitTime) : TimeDelta.NULL;
            return this;
        }

        /**
         * Sets the travel time of newly built nodes.
         * @param travelTime the travel time of newly built nodes
         * @return this
         */
        public Builder setTravelTime(long travelTime) {
            this.travelTime = (travelTime != 0) ? new TimeDelta(travelTime) : TimeDelta.NULL;
            return this;
        }

        /**
         * Builds the node.
         * @return the newly built node
         * @throws IllegalStateException if the node would be invalid, eg if no point was set.
         */
        public TravelRouteNode build() {
            if (this.pt == null) {
                LoggingUtils.logError("TravelRouteNode.Builder", "Point not set.");
                throw new IllegalStateException("Method Builder::setPoint must be called before Builder::build.");
            }
            return new TravelRouteNode(pt, walkTime, waitTime, travelTime);
        }
    }
}
