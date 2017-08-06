package com.reroute.backend.locations.providers;

import com.reroute.backend.locations.foursquare.FoursquareLocationsProvider;
import com.reroute.backend.locations.gmaps.GoogleLocationsProvider;
import com.reroute.backend.locations.interfaces.LocationProvider;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class LocationsProvidersTest {

    @Before
    public void init() {
        LoggingUtils.setPrintImmediate(true);
    }

    @Test
    public void testGoogle() throws Exception {
        testProvider(new GoogleLocationsProvider());
    }

    private void testProvider(LocationProvider provider) throws Exception{
        LocationPoint testPt = new StartPoint(new double[] { 37.3532801, -122.0052875 });
        Distance testRange = new Distance(1, DistanceUnits.MILES);
        LocationType testType = new LocationType("Food", "food");

        List<DestinationLocation> testDests = provider.queryLocations(testPt, testRange, testType);
        Assert.assertTrue(testDests.size() > 0);
        System.out.println("PROVIDER: "+provider.getClass().getName());
        testDests.forEach(dest -> {
            double dist = LocationUtils.distanceBetween(dest, testPt).inMiles();
                    System.out.printf("%s @ (%f, %f), %f miles from center.\n",
                            dest.getName(),
                            dest.getCoordinates()[0], dest.getCoordinates()[1],
                            dist);
            Assert.assertTrue(dist <= testRange.inMiles());
                }
        );
        System.out.printf("Total: %d\n\n", testDests.size());
    }

    @Test
    public void testFourSquare() throws Exception {
        testProvider(new FoursquareLocationsProvider());
    }

    @After
    public void deInit(){
        LoggingUtils.setPrintImmediate(false);
    }

}