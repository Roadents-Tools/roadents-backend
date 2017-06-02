package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public class StationDbHelper {

    private static StationDbHelper instance;
    private static boolean isTest = false;
    private StationDbInstance[] allDatabases = null;

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

        /*StationDbInstance[] allDbs = new StationDbInstance[MysqlStationDb.DB_URLS.length + 1];
        allDbs[0] = new TransitlandApiDb();
        for (int i = 1; i < MysqlStationDb.DB_URLS.length + 1; i++) {
            allDbs[i] = new MysqlStationDb(MysqlStationDb.DB_URLS[i - 1]);
        }
        allDatabases = allDbs;*/
        allDatabases = new StationDbInstance[] { new TransitlandApiDb() };
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

    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.AreaDb)
                .flatMap(db -> ((StationDbInstance.AreaDb) db).queryStations(center, range, chain)
                        .stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<TransStation> queryStations(TimePoint start, TimeDelta range, TransChain chain) {
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.ScheduleDb)
                .flatMap(db -> ((StationDbInstance.ScheduleDb) db).queryStations(start, range, chain)
                        .stream())
                .distinct()
                .collect(Collectors.toList());
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

    public boolean putStations(List<TransStation> stations) {
        //We create a boolean set and then check if any are true
        //to guarantee that all instances are attempted.
        return Arrays.stream(allDatabases).parallel()
                .map(db -> db.putStations(stations))
                .collect(Collectors.toSet())
                .contains(true);
    }
}
