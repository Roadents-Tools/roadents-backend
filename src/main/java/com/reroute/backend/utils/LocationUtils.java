package com.reroute.backend.utils;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.time.TimeDelta;

/**
 * Created by ilan on 7/8/16.
 */
public class LocationUtils {

    /**
     * Constants For Math
     **/
    private static final double EARTH_RADIUS_KM = 6367.449; //kilometers
    private static final double AVG_WALKING_SPEED_KPH = 5.0;
    private static final Distance MAX_TRANSIT_PER_HOUR = new Distance(65, DistanceUnits.MILES);
    private static final double SAFETY_FACTOR = 1;

    public static TimeDelta timeBetween(LocationPoint a, LocationPoint b) {
        return distanceToWalkTime(distanceBetween(a, b));
    }

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

    public static TimeDelta distanceToWalkTime(Distance distance) {
        double hours = distance.inKilometers() / AVG_WALKING_SPEED_KPH;
        double millis = hours * 1000.0 * 60.0 * 60.0 * SAFETY_FACTOR;
        return new TimeDelta((long) millis);
    }

    public static Distance timeToMaxTransit(TimeDelta delta) {
        return MAX_TRANSIT_PER_HOUR.mul(delta.inHours());
    }

    public static Distance timeToWalkDistance(TimeDelta time) {
        double km = (AVG_WALKING_SPEED_KPH * time.inHours()) / SAFETY_FACTOR;
        return new Distance(km, DistanceUnits.KILOMETERS);
    }

}
