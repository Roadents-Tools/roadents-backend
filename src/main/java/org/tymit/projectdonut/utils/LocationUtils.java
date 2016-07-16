package org.tymit.projectdonut.utils;

/**
 * Created by ilan on 7/8/16.
 */
public class LocationUtils {

    /**
     * Constants For Math
     **/
    private static final double EARTH_RADIUS_MI = 3958.75; //miles
    private static final double EARTH_RADIUS_KM = 6371.00; //kilometers
    private static final double AVG_WALKING_SPEED_MPH = 3.1075;
    private static final double AVG_WALKING_SPEED_KPH = 5.0;
    private static final double SAFETY_FACTOR = 2;


    public static double distanceBetween(double[] l1, double[] l2, boolean miles) {

        double dLat = Math.toRadians(l2[0] - l1[0]);
        double dLng = Math.toRadians(l2[1] - l1[1]);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(l1[0]) * Math.cos(Math.toRadians(l2[0])));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = c * ((miles) ? EARTH_RADIUS_MI : EARTH_RADIUS_KM);

        return dist;
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
}
