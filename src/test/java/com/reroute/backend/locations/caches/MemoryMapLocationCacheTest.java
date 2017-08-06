package com.reroute.backend.locations.caches;

import com.reroute.backend.locations.memory.MemoryMapLocationCache;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Created by ilan on 8/31/16.
 */
public class MemoryMapLocationCacheTest {

    @Test
    public void testCache() {

        /* SETUP */
        MemoryMapLocationCache testCache = new MemoryMapLocationCache();
        Random rng = new Random(123);
        List<LocationType> types = new ArrayList<>();
        Map<LocationType, Set<DestinationLocation>> trueVals = new HashMap<>();
        LocationPoint startPoint = new StartPoint(new double[] { 60, 60 });
        Distance mile = new Distance(1, DistanceUnits.MILES);
        for (int i = 0; i < 10; i++) {
            String typeName = rng.ints(5).collect(StringBuilder::new, (r, value) -> r.append(value), (r, r2) -> r.append(r2.toString())).toString();
            LocationType type = new LocationType(typeName, typeName);
            types.add(type);

            Set<DestinationLocation> dests = new HashSet<>();
            for (int j = 0; j < 10; j++) {
                String destName = rng.ints(7).collect(StringBuilder::new, (r, value) -> r.append(value), (r, r2) -> r.append(r2.toString())).toString();
                DestinationLocation dest = new DestinationLocation(destName, type, startPoint.getCoordinates());
                dests.add(dest);
            }
            trueVals.put(type, dests);
            testCache.cacheLocations(startPoint, mile, type, new ArrayList<>(dests));
        }

        /* TEST */
        for (LocationType type : types) {
            List<DestinationLocation> actualEqual = testCache.getCachedLocations(startPoint, mile, type);
            List<DestinationLocation> actualLess = testCache.getCachedLocations(startPoint, mile.div(2), type);
            List<DestinationLocation> actualGreater = testCache.getCachedLocations(startPoint, mile.mul(1.5), type);

            Assert.assertEquals(trueVals.get(type), new HashSet<>(actualEqual));
            Assert.assertEquals(trueVals.get(type), new HashSet<>(actualLess));
            Assert.assertEquals(null, actualGreater);
        }
    }

}