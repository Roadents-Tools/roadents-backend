package com.reroute.backend.utils;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;

/**
 * Contains a variety of utility and helper functions related to Locations.
 * Created by ilan on 7/8/16.
 */
public class LocationUtils {

    /**
     * Constants For Math
     **/
    private static final double EARTH_RADIUS_KM = 6367.449; //kilometers
    private static final Distance LENGTH_DEGREE_LAT = new Distance(111, DistanceUnits.KILOMETERS);
    private static final Distance EQUATOR_LENGTH_DEGREE_LNG = new Distance(111.321, DistanceUnits.KILOMETERS);

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

    public static double latitudeRange(LocationPoint center, Distance range) {
        return range.inKilometers() / LENGTH_DEGREE_LAT.inKilometers();
    }

    public static double longitudeRange(LocationPoint center, Distance range) {
        double unit = Math.cos(center.getCoordinates()[0]) * EQUATOR_LENGTH_DEGREE_LNG.inKilometers();
        return range.inKilometers() / unit;
    }

}
