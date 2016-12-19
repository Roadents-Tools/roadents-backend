package org.tymit.projectdonut.locations.providers;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

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

    @Test
    public void testFourSquare() throws Exception {
        testProvider(new FoursquareLocationsProvider());
    }

    private void testProvider(LocationProvider provider) throws Exception{
        double[] testPt = new double[]{37.3532801, -122.0052875};
        double testRange = 1;
        LocationType testType = new LocationType("Food", "food");

        List<DestinationLocation> testDests = provider.queryLocations(testPt, testRange, testType);
        Assert.assertTrue(testDests.size() > 0);
        System.out.println("PROVIDER: "+provider.getClass().getName());
        testDests.forEach(dest -> {
                    double dist = LocationUtils.distanceBetween(dest.getCoordinates(), testPt, true);
                    System.out.printf("%s @ (%f, %f), %f miles from center.\n",
                            dest.getName(),
                            dest.getCoordinates()[0], dest.getCoordinates()[1],
                            dist);
                    Assert.assertTrue(dist <= testRange);
                }
        );
        System.out.printf("Total: %d\n\n", testDests.size());
    }

    @After
    public void deInit(){
        LoggingUtils.setPrintImmediate(false);
    }

}