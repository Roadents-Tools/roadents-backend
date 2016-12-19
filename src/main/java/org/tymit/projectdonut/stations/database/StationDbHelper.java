package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.stations.caches.StationCacheHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        StationDbInstance[] allDbs = new StationDbInstance[MysqlStationDb.DB_URLS.length];
        for (int i = 0; i < MysqlStationDb.DB_URLS.length; i++) {
            allDbs[i] = new MysqlStationDb(MysqlStationDb.DB_URLS[i]);
        }
        allDatabases = allDbs;
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
        if (!isTest && chain == null) {
            List<TransStation> cached = StationCacheHelper.getHelper().getCachedStations(center, range);
            if (cached != null && cached.size() > 0) {
                return cached;
            }
        }

        Set<TransStation> allStations = new HashSet<>();
        for (StationDbInstance dbInstance : allDatabases) {
            if (dbInstance.isUp()) allStations.addAll(dbInstance.queryStations(center, range, chain));
        }

        List<TransStation> rval = new ArrayList<>(allStations);
        if (!isTest && chain == null) StationCacheHelper.getHelper().cacheStations(center, range, rval);
        return rval;
    }

    public boolean putStations(List<TransStation> stations) {
        //We create a boolean set and then check if any are true
        //to guarantee that all instances are attempted.
        return Arrays.asList(allDatabases).parallelStream()
                .map(db -> db.putStations(stations))
                .collect(Collectors.toSet())
                .contains(true);
    }
}
