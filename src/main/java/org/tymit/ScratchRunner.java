package org.tymit;

import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.postgresql.PostgresqlStationDbCache;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

    }


    private static boolean loadtransitzips(String rootdirectory, String dburl) {
        PostgresqlStationDbCache db = new PostgresqlStationDbCache(dburl);
        File rootFile = new File(rootdirectory);
        return Arrays.stream(rootFile.listFiles())
                .parallel()
                .map(GtfsProvider::new)
                .map(GtfsProvider::getUpdatedStations)
                .allMatch(col -> {
                    for (List<TransStation> stats : col.values()) {
                        if (!db.putStations(stats)) return false;
                    }
                    return true;
                });
    }

}

