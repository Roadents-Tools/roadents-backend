package org.tymit.projectdonut.utils;

import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.time.TimeDelta;

/**
 * Created by ilan on 7/8/16.
 */
public class LocationUtils {

    /**
     * Constants For Math
     **/
    public static final double EARTH_RADIUS_MI = metersToMiles(6367449); //miles
    public static final double EARTH_RADIUS_KM = 6367.449; //kilometers
    private static final double AVG_WALKING_SPEED_MPH = 3.1075;
    private static final double AVG_WALKING_SPEED_KPH = 5.0;
    private static final double SAFETY_FACTOR = 1;

    public static long timeBetween(double[] l1, double[] l2) {
        return distanceToWalkTime(distanceBetween(l1, l2, true), true);
    }

    public static TimeDelta timeBetween(LocationPoint a, LocationPoint b) {
        return new TimeDelta(timeBetween(a.getCoordinates(), b.getCoordinates()));
    }

    public static double distanceBetween(double[] l1, double[] l2, boolean miles) {

        double dLat = Math.toRadians(l2[0] - l1[0]);
        double dLng = Math.toRadians(l2[1] - l1[1]);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(l1[0]) * Math.cos(Math.toRadians(l2[0])));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return c * ((miles) ? EARTH_RADIUS_MI : EARTH_RADIUS_KM);
    }

    public static double distanceBetween(LocationPoint a, LocationPoint b, boolean miles) {
        return distanceBetween(a.getCoordinates(), b.getCoordinates(), miles);
    }

    public static long distanceToWalkTime(double distance, boolean miles) {
        double hours = (miles) ? distance / AVG_WALKING_SPEED_MPH : distance / AVG_WALKING_SPEED_KPH;
        double millis = hours * 1000.0 * 60.0 * 60.0 * SAFETY_FACTOR;
        return (long) millis;
    }

    public static double timeToWalkDistance(long time, boolean miles) {
        double timeHours = time / 1000.0 / 60.0 / 60.0;
        return (miles) ? AVG_WALKING_SPEED_MPH * timeHours / SAFETY_FACTOR : AVG_WALKING_SPEED_KPH * timeHours / SAFETY_FACTOR;
    }

    public static double milesToMeters(double miles){
        return miles * 1000 * AVG_WALKING_SPEED_KPH/AVG_WALKING_SPEED_MPH;
    }

    public static double metersToMiles(double meters) {
        return meters / 1000 * AVG_WALKING_SPEED_MPH / AVG_WALKING_SPEED_KPH;
    }
}
