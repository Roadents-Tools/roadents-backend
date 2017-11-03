package com.reroute.backend.utils;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.time.TimeDelta;

/**
 * Contains a variety of utility and helper functions related to Locations.
 * Created by ilan on 7/8/16.
 */
public class LocationUtils {

    /**
     * Constants For Math
     **/
    private static final double EARTH_RADIUS_KM = 6367.449; //kilometers
    private static final Distance AVG_WALKING_PER_HOUR = new Distance(5.0, DistanceUnits.KILOMETERS);
    private static final Distance MAX_TRANSIT_PER_HOUR = new Distance(65, DistanceUnits.MILES);
    private static final double SAFETY_FACTOR = 1;

    /**
     * Gets the straight-line walking time between 2 points.
     *
     * @param a the point to start walking from
     * @param b the point to finish walking at
     * @return the time to walk
     */
    public static TimeDelta timeBetween(LocationPoint a, LocationPoint b) {
        return distanceToWalkTime(distanceBetween(a, b));
    }

    /**
     * Gets the distance between 2 latitude-longitude points, calculated assuming a spherical Earth.
     * @param p1 the point to start at
     * @param p2 the point to finish at
     * @return the distance between the 2 points
     */
    public static Distance distanceBetween(LocationPoint p1, LocationPoint p2) {
        double[] l1 = p1.getCoordinates();
        double[] l2 = p2.getCoordinates();

        double dLat = Math.toRadians(l2[0] - l1[0]);
        double dLng = Math.toRadians(l2[1] - l1[1]);

        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);

        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(l1[0]) * Math.cos(Math.toRadians(l2[0])));

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double ckm = c * EARTH_RADIUS_KM;
        return new Distance(ckm, DistanceUnits.KILOMETERS);
    }

    /**
     * Gets the time to straight-line walk a given distance.
     * @param distance the distance to walk
     * @return the time it takes
     */
    public static TimeDelta distanceToWalkTime(Distance distance) {
        double hours = distance.inMeters() / AVG_WALKING_PER_HOUR.inMeters();
        double millis = hours * 1000.0 * 60.0 * 60.0 * SAFETY_FACTOR;
        return new TimeDelta((long) millis);
    }

    /**
     * Gets the maximum straight-line distance that can theoretically be covered by transit in a given time.
     * @param delta the time to spend in transit
     * @return the maximum distance possible to travel
     */
    public static Distance timeToMaxTransit(TimeDelta delta) {
        return MAX_TRANSIT_PER_HOUR.mul(delta.inHours());
    }

    /**
     * Gets the maximum straight-line distance that can be walked in a given time.
     * @param time the time spent walking
     * @return the maximum distance possible to walk
     */
    public static Distance timeToWalkDistance(TimeDelta time) {
        return AVG_WALKING_PER_HOUR.mul(time.inHours()).div(SAFETY_FACTOR);
    }

}
