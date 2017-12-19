package com.reroute.backend.stations.helpers;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.interfaces.StationCacheInstance;
import com.reroute.backend.stations.redis.RedisDonutCache;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by ilan on 8/31/16.
 */
public class StationChainCacheHelper {

    private static StationChainCacheHelper instance = new StationChainCacheHelper();

    private StationCacheInstance.DonutCache[] donutCaches;
    private boolean isTest = false;

    private StationChainCacheHelper() {
        initializeStationInstanceList();
    }

    private void initializeStationInstanceList() {
        if (isTest) {
            donutCaches = new StationCacheInstance.DonutCache[0];
            return;
        }
        donutCaches = new StationCacheInstance.DonutCache[] {
                new RedisDonutCache("127.0.0.1", 6379)
        };
    }

    public static void setTestMode(boolean testMode) {
        instance.isTest = testMode;
        instance.initializeStationInstanceList();
    }

    public static StationChainCacheHelper getHelper() {
        return instance;
    }

    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (isTest || center == null || range.inMeters() < 0) {
            return Collections.emptyList();
        }


        return Arrays.stream(donutCaches)
                .map(cache -> cache.getStationsInArea(center, range))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElse(Collections.emptyList());
    }

    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        if (isTest || station == null) {
            return Collections.emptyMap();
        }

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getChainsForStation(station))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElse(Collections.emptyMap());
    }

    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station, TimePoint startTime, TimeDelta maxDelta) {
        if (isTest || station == null) {
            return Collections.emptyMap();
        }

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getChainsForStation(station, startTime, maxDelta))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElse(Collections.emptyMap());
    }

    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (isTest || chain == null) {
            return Collections.emptyMap();
        }

        return Arrays.stream(donutCaches)
                .map(cache -> cache.getArrivableStations(chain, startTime, maxDelta))
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .findAny()
                .orElseGet(Collections::emptyMap);
    }


    public boolean putArea(LocationPoint center, Distance range, List<TransStation> stations) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.putArea(center, range, stations));
    }

    public boolean putWorld(WorldInfo request, Map<TransChain, Map<TransStation, List<SchedulePoint>>> world) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.putWorld(request, world));
    }

    public boolean hasWorld(WorldInfo request) {
        return Arrays.stream(donutCaches)
                .anyMatch(cache -> cache.hasWorld(request));
    }

    public void closeAllCaches() {
        Arrays.stream(donutCaches).forEach(StationCacheInstance::close);
    }
}
