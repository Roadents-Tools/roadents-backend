package org.tymit.projectdonut.stations.helpers;

import org.tymit.projectdonut.model.location.LocationPoint;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 8/31/16.
 */
public class StationChainCacheHelper {

    private static StationChainCacheHelper instance = new StationChainCacheHelper();

    private StationCacheInstance.GeneralCache[] allStationInstances;
    private StationCacheInstance.DonutCache[] donutCaches;
    private boolean isTest = false;

    private StationChainCacheHelper() {
        initializeStationInstanceList();
    }

    private void initializeStationInstanceList() {
        if (isTest) allStationInstances = new StationCacheInstance.GeneralCache[0];
        allStationInstances = new StationCacheInstance.GeneralCache[] {
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

    public List<TransStation> getCachedStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        if (isTest || allStationInstances == null || allStationInstances.length == 0) return Collections.emptyList();
        return Arrays.stream(allStationInstances)
                .parallel()
                .map(cache -> cache.getCachedStations(center, range, startTime, maxDelta, chain))
                .filter(Objects::nonNull)
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
    }

    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, Stream<List<TransStation>> stations) {
        if (isTest) return true;
        if (allStationInstances == null || allStationInstances.length == 0) return false;
        return stations
                .anyMatch(src -> cacheStations(center, range, startTime, maxDelta, src));
    }

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


    public List<TransStation> getStationsInArea(LocationPoint center, double range) {
        if (isTest || center == null || range < 0) {
            return Collections.emptyList();
        }

        Supplier<List<TransStation>> fallback = () -> getCachedStations(center.getCoordinates(), range, null, null, null);

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getStationsInArea(center, range))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElseGet(fallback);
    }

    public List<TransChain> getChainsForStation(TransStation station) {
        if (isTest || station == null) {
            return Collections.emptyList();
        }

        Supplier<List<TransChain>> fallback = () -> getCachedStations(station.getCoordinates(), 0, null, null, null)
                .stream()
                .map(TransStation::getChain)
                .distinct()
                .collect(Collectors.toList());

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getChainsForStation(station))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElseGet(fallback);
    }

    public List<TransStation> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (isTest || chain == null) {
            return Collections.emptyList();
        }

        Supplier<List<TransStation>> fallback = () -> getCachedStations(null, -1, startTime, maxDelta, chain);

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getArrivableStations(chain, startTime, maxDelta))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElseGet(fallback);
    }


    public boolean putArea(LocationPoint center, double range, List<TransStation> stations) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.putArea(center, range, stations));
    }

    public boolean putChainsForStation(TransStation station, List<TransChain> chains) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.putChainsForStation(station, chains));
    }

    public boolean putStationsForChain(TransChain chain, List<TransStation> stations) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.putStationsForChain(chain, stations));
    }


    public void closeAllCaches() {
        if (allStationInstances == null) return;
        for (StationCacheInstance instance : allStationInstances) {
            instance.close();
        }
    }
}
