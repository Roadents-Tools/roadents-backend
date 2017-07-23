package org.tymit.projectdonut.stations.helpers;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationCacheInstance;
import org.tymit.projectdonut.stations.memory.FatMemoryCache;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 8/31/16.
 */
public class StationChainCacheHelper {

    private static StationChainCacheHelper instance = new StationChainCacheHelper();

    private StationCacheInstance[] allStationInstances;
    private boolean isTest = false;

    private StationChainCacheHelper() {
        initializeStationInstanceList();
    }

    private void initializeStationInstanceList() {
        if (isTest) allStationInstances = new StationCacheInstance[0];
        allStationInstances = new StationCacheInstance[] {
                new FatMemoryCache()
        };
    }

    public static void setTestMode(boolean testMode) {
        instance.isTest = testMode;
        instance.initializeStationInstanceList();
    }

    public static StationChainCacheHelper getHelper() {
        return instance;
    }

    @Deprecated
    public List<TransStation> getCachedStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        if (isTest || allStationInstances == null || allStationInstances.length == 0) return Collections.emptyList();
        return Arrays.stream(allStationInstances)
                .parallel()
                .map(cache -> cache.getCachedStations(center, range, startTime, maxDelta, chain))
                .filter(Objects::nonNull)
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
    }

    public List<TransStation> getCachedStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        if (isTest || allStationInstances == null || allStationInstances.length == 0) return Collections.emptyList();
        return Arrays.stream(allStationInstances)
                .parallel()
                .map(cache -> cache.getCachedStations(center, range, startTime, maxDelta, chain))
                .filter(Objects::nonNull)
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
    }

    @Deprecated
    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, Stream<List<TransStation>> stations) {
        if (isTest) return true;
        if (allStationInstances == null || allStationInstances.length == 0) return false;
        return stations
                .map(src -> cacheStations(center, range, startTime, maxDelta, src))
                .distinct()
                .allMatch(Boolean::booleanValue);
    }

    @Deprecated
    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {

        if (isTest) return true;
        if (allStationInstances == null || allStationInstances.length == 0) return false;

        //Since physical range is easily calculable even without being given it,
        //we do so for possible efficiencies in the future.
        //However, the same is not true for temporal range.
        if (center == null || range < 0) {
            center = new double[] { 0, 0 };
            int size = 0;

            for (TransStation stat : stations) {
                center[0] += stat.getCoordinates()[0];
                center[1] += stat.getCoordinates()[1];
                size++;
            }

            center[0] = center[0] / size;
            center[1] = center[1] / size;

            for (TransStation stat : stations) {
                double curange = LocationUtils.distanceBetween(center, stat.getCoordinates(), true);
                if (curange > range) range = curange;
            }
        }

        //Java is BS sometimes
        double[] finalCenter = center;
        double finalRange = range;


        return Arrays.stream(allStationInstances)
                .parallel()
                .anyMatch(cache -> cache.cacheStations(finalCenter, finalRange, startTime, maxDelta, stations));

    }

    public boolean cacheStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {

        if (isTest) return true;
        if (allStationInstances == null || allStationInstances.length == 0) return false;

        //Since physical range is easily calculable even without being given it,
        //we do so for possible efficiencies in the future.
        //However, the same is not true for temporal range.
        if (center == null || range.inMeters() < 0) {
            double[] centerArr = new double[] { 0, 0 };
            int size = 0;

            for (TransStation stat : stations) {
                centerArr[0] += stat.getCoordinates()[0];
                centerArr[1] += stat.getCoordinates()[1];
                size++;
            }

            centerArr[0] = centerArr[0] / size;
            centerArr[1] = centerArr[1] / size;

            center = new StartPoint(centerArr);

            for (TransStation stat : stations) {
                Distance curange = LocationUtils.distanceBetween(center, stat);
                if (curange.inMeters() > range.inMeters()) range = curange;
            }
        }

        //Really Java?
        LocationPoint finalCenter = center;
        Distance finalRange = range;

        return Arrays.stream(allStationInstances)
                .parallel()
                .anyMatch(cache -> cache.cacheStations(finalCenter, finalRange, startTime, maxDelta, stations));

    }
    public void closeAllCaches() {
        if (allStationInstances == null) return;
        for (StationCacheInstance instance : allStationInstances) {
            instance.close();
        }
    }
}
