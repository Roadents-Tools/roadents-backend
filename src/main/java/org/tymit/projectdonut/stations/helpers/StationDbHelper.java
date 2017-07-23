package org.tymit.projectdonut.stations.helpers;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.stations.test.TestStationDb;
import org.tymit.projectdonut.stations.transitland.TransitlandApiDb;

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

    @Deprecated
    public List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        return Arrays.stream(allDatabases)
                .filter(StationDbInstance::isUp)
                .filter(db -> db instanceof StationDbInstance.ComboDb)
                .flatMap(db -> ((StationDbInstance.ComboDb) db).queryStations(center, range, startTime, maxDelta, chain)
                        .stream())
                .distinct()
                .collect(Collectors.toList());
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
}
