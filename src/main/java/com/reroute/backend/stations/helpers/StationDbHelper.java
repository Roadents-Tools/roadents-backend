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

        allDatabases = Arrays.stream(PostgresqlDonutDb.DB_URLS)
                .map(PostgresqlDonutDb::new)
                .toArray(StationDbInstance[]::new);

        Arrays.stream(allDatabases)
                .filter(instance -> instance instanceof StationDbInstance.DonutDb)
                .map(instance -> (StationDbInstance.DonutDb) instance)
                .forEach(ddb -> nameToDbs.computeIfAbsent(ddb.getSourceName(), (k) -> new ArrayList<>()).add(ddb));
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

    public void closeAllDatabases() {
        Arrays.stream(allDatabases).forEach(StationDbInstance::close);
    }

    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        Supplier<List<TransStation>> orElseSupplier = ArrayList::new;

        return doDonutQuery(db -> db.getStationsInArea(center, range), orElseSupplier);
    }

    private <T> T doDonutQuery(Function<StationDbInstance.DonutDb, T> toGet, Supplier<T> orElse) {
        return nameToDbs.values().stream()
                .flatMap(Collection::stream)
                .filter(StationDbInstance::isUp)
                .map(toGet)
                .findAny()
                .orElseGet(orElse);
    }

    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        return doDonutQuery(db -> db.getChainsForStation(station), Collections::emptyMap);
    }

    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        return doDonutQuery(db -> db.getArrivableStations(chain, startTime, maxDelta), Collections::emptyMap);
    }

    public Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(WorldInfo info) {
        return doDonutQuery(db -> db.getWorld(info.getCenter(), info.getRange(), info.getStartTime(), info.getMaxDelta()), Collections::emptyMap);
    }
}
