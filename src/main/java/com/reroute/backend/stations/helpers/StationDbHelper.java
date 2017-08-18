package com.reroute.backend.stations.helpers;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.interfaces.StationDbInstance;
import com.reroute.backend.stations.postgresql.PostgresqlDonutDb;
import com.reroute.backend.stations.test.TestStationDb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public class StationDbHelper {

    private static StationDbHelper instance;
    private static boolean isTest = false;
    private StationDbInstance[] allDatabases = null;
    private Map<String, List<StationDbInstance.DonutDb>> nameToDbs = new HashMap<>();

    private StationDbHelper() {
        initializeDbList();
    }

    private void initializeDbList() {
        if (isTest) {
            allDatabases = new StationDbInstance[]{new TestStationDb()};
            return;
        }

        allDatabases = new StationDbInstance[] {
                new PostgresqlDonutDb(PostgresqlDonutDb.DB_URLS[1])
        };

        for (StationDbInstance instance : allDatabases) {
            if (!(instance instanceof StationDbInstance.DonutDb)) continue;
            StationDbInstance.DonutDb ddb = (StationDbInstance.DonutDb) instance;
            nameToDbs.computeIfAbsent(ddb.getSourceName(), (k) -> new ArrayList<>()).add(ddb);
        }
    }

    public static StationDbHelper getHelper() {
        if (instance == null) instance = new StationDbHelper();
        return instance;
    }

    public static void setTestMode(boolean testMode) {
        if (isTest == testMode) return;
        isTest = testMode;
        instance = null;
        TestStationDb.setTestStations(null);
    }

    public List<TransStation> queryStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .flatMap(db -> ((StationDbInstance.ComboDb) db).queryStations(center, range, startTime, maxDelta, chain)
                        .stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<TransStation> queryStrippedStations(LocationPoint center, Distance range, int limit) {
        if (center == null || range == null || range.inMeters() <= 0 || limit == 0) return Collections.emptyList();
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .findAny()
                .map(db -> ((StationDbInstance.ComboDb) db).queryStrippedStations(center, range, limit))
                .orElse(Collections.emptyList());
    }

    public boolean putStations(List<TransStation> stations) {
        //We create a boolean set and then check if any are true
        //to guarantee that all instances are attempted.
        return Arrays.stream(allDatabases).parallel()
                .map(db -> db.putStations(stations))
                .collect(Collectors.toSet())
                .contains(true);
    }

    public void closeAllDatabases() {
        Arrays.stream(allDatabases).forEach(StationDbInstance::close);
    }

    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        Supplier<List<TransStation>> orElseSupplier = ArrayList::new;

        return doDonutQuery(db -> db.getStationsInArea(center, range), orElseSupplier);
    }

    public Map<LocationPoint, List<TransStation>> getStationsInArea(Map<LocationPoint, Distance> ranges) {
        Supplier<Map<LocationPoint, List<TransStation>>> orElseSupplier = HashMap::new;

        return doDonutQuery(db -> db.getStationsInArea(ranges), orElseSupplier);

    }

    private <T> T doDonutQuery(Function<StationDbInstance.DonutDb, T> toGet, Supplier<T> orElse) {
        return nameToDbs.values().stream()
                .flatMap(Collection::stream)
                .filter(StationDbInstance::isUp)
                .map(toGet)
                .findAny()
                .orElseGet(orElse);
    }

    private <T> T doComboQuery(Function<StationDbInstance.ComboDb, T> toGet, Supplier<T> orElse) {
        return Arrays.stream(allDatabases)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .filter(StationDbInstance::isUp)
                .map(db -> (StationDbInstance.ComboDb) db)
                .map(toGet)
                .findAny()
                .orElseGet(orElse);
    }

    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        return doDonutQuery(db -> db.getChainsForStation(station), Collections::emptyMap);
    }

    public Map<TransStation, Map<TransChain, List<SchedulePoint>>> getChainsForStations(List<TransStation> stations) {
        return doDonutQuery(db -> db.getChainsForStations(stations), Collections::emptyMap);
    }

    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        return doDonutQuery(db -> db.getArrivableStations(chain, startTime, maxDelta), Collections::emptyMap);
    }

    public Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(List<TransChain> chains, TimePoint startTime, TimeDelta maxDelta) {
        return doDonutQuery(db -> db.getArrivableStations(chains, startTime, maxDelta), Collections::emptyMap);
    }

    public Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(Map<TransChain, TimeDelta> chainsAndExtras, TimePoint generalStart, TimeDelta maxDelta) {
        return doDonutQuery(db -> db.getArrivableStations(chainsAndExtras, generalStart, maxDelta), Collections::emptyMap);
    }

    public Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(WorldInfo info) {
        return doDonutQuery(db -> db.getWorld(info.getCenter(), info.getRange(), info.getStartTime(), info.getMaxDelta()), Collections::emptyMap);
    }
}
