package org.tymit.projectdonut.locations.providers;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.utils.LoggingUtils;
import se.walkercrou.places.GooglePlaces;
import se.walkercrou.places.Param;
import se.walkercrou.places.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class GoogleLocationsProvider implements LocationProvider {

    public static final String[] API_KEYS = {
            "AIzaSyB0pBdXuC4VRte73qnVtE5pLmxNs3ju0Gg"
    };
    private static final int MAX_QUERY_CALLS = 2;
    private static double MILES_TO_METERS = 1609.344;
    private GooglePlaces gmaps;
    private int queryCalls;

    public GoogleLocationsProvider() {
        gmaps = new GooglePlaces(API_KEYS[0]);
        queryCalls = 0;
    }

    @Override
    public boolean isUsable() {
        return true; //We only have the one, so we always have to use it no matter what.
    }

    @Override
    public boolean isValidType(LocationType type) {
        return true; //Google can basically handle anything.
    }

    @Override
    public List<DestinationLocation> queryLocations(double[] center, double range, LocationType type) {
        if (queryCalls >= MAX_QUERY_CALLS) {
            queryCalls = 0;
            gmaps = new GooglePlaces(API_KEYS[0]);
        }
        queryCalls++;
        double rangeInMeters = range * MILES_TO_METERS;
        try {
            List<Place> places = gmaps.getNearbyPlaces(
                    center[0], center[1], rangeInMeters,
                    Param.name("type").value(type.getEncodedname())
            );
            List<DestinationLocation> rval = new ArrayList<>(places.size());
            for (Place place : places) {
                String placeName = place.getName();
                rval.add(new DestinationLocation(placeName, type, new double[]{place.getLatitude(), place.getLongitude()}));
            }
            return rval;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return new ArrayList<>();
        }
    }


}
