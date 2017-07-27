package org.tymit.projectdonut.stations.helpers;

import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.stations.postgresql.PostgresqlStationDbCache;
import org.tymit.projectdonut.stations.test.TestStationDb;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public class StationDbHelper {

    private static StationDbHelper instance;
    private static boolean isTest = false;
    private StationDbInstance[] allDatabases = null;
    Map<String, List<StationDbInstance.DonutDb>> nameToDbs = new HashMap<>();

    private StationDbHelper() {
        initializeDbList();
    }

    private void initializeDbList() {

        if (allDatabases != null) {
            for (StationDbInstance db : allDatabases) {
                db.close();
            }
        }

        if (isTest) {
            allDatabases = new StationDbInstance[]{new TestStationDb()};
            return;
        }
        allDatabases = new StationDbInstance[] {
                new PostgresqlStationDbCache(PostgresqlStationDbCache.DB_URLS[0])
        };
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

    public List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .flatMap(db -> ((StationDbInstance.ComboDb) db).queryStations(center, range, startTime, maxDelta, chain)
                        .stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<TransStation> queryStrippedStations(double[] center, double range, int limit) {
        if (center == null || range <= 0 || limit == 0) return Collections.emptyList();
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
        for (StationDbInstance instance : allDatabases) {
            instance.close();
        }
    }

    public List<TransStation> getStationsInArea(LocationPoint center, double range) {
        Supplier<List<TransStation>> orElseSupplier = () -> Arrays.stream(allDatabases)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .filter(StationDbInstance::isUp)
                .findAny()
                .map(db -> ((StationDbInstance.ComboDb) db).queryStrippedStations(center.getCoordinates(), range, 10000))
                .orElse(Collections.emptyList());

        return nameToDbs.values().stream()
                .filter(l -> !l.isEmpty())
                .findAny()
                .flatMap(l -> l.stream().findAny())
                .map(db -> db.getStationsInArea(center, range))
                .orElseGet(orElseSupplier);

    }

    public List<TransChain> getChainsForStation(TransStation station) {
        Supplier<List<TransChain>> fallback = () -> Arrays.stream(allDatabases)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .filter(StationDbInstance::isUp)
                .findAny()
                .map(db -> ((StationDbInstance.ComboDb) db).queryStations(station.getCoordinates(), 0, null, null, null))
                .map(stations -> stations.stream()
                        .map(TransStation::getChain)
                        .distinct()
                        .collect(Collectors.toList())
                )
                .orElse(Collections.emptyList());

        String dbname = station.getID() != null ? station.getID().getDatabaseName() : null;
        return nameToDbs.getOrDefault(dbname, Collections.emptyList())
                .stream()
                .filter(StationDbInstance::isUp)
                .findAny()
                .map(db -> db.getChainsForStation(station))
                .orElseGet(fallback);
    }

    public List<TransStation> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        Supplier<List<TransStation>> fallback = () -> Arrays.stream(allDatabases)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .filter(StationDbInstance::isUp)
                .findAny()
                .map(db -> ((StationDbInstance.ComboDb) db).queryStations(null, -1, startTime, maxDelta, chain))
                .orElse(Collections.emptyList());

        String dbname = chain.getID() != null ? chain.getID().getDatabaseName() : null;
        return nameToDbs.getOrDefault(dbname, Collections.emptyList())
                .stream()
                .filter(StationDbInstance::isUp)
                .findAny()
                .map(db -> db.getArrivableStations(chain, startTime, maxDelta))
                .orElseGet(fallback);
    }
}
