package org.tymit.projectdonut.model;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationUtils {

    private static final double EARTH_RADIUS = 3958.75; //miles

    public static double distanceBetween(double[] l1, double[] l2) {

        double dLat = Math.toRadians(l2[0] - l1[0]);
        double dLng = Math.toRadians(l2[1] - l1[1]);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(l1[0]) * Math.cos(Math.toRadians(l2[0])));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = EARTH_RADIUS * c;

        return dist;
    }
}
