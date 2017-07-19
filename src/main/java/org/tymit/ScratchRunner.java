package org.tymit;

import com.google.common.io.Files;
import org.tymit.displayers.testdisplay.mapsareadrawer.MapsPageGenerator;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.postgresql.PostgresqlStationDbCache;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

        TimePoint startTime = TimePoint.NULL
                .withYear(2017)
                .withDayOfWeek(3)
                .withHour(10)
                .withMinute(0)
                .withSecond(0);

        TimeDelta maxDelta = new TimeDelta(30 * 60 * 1000);

        List<String> sites = MapsPageGenerator
                .generateIndividualPagesFromFile("/home/ilan/latlngs.txt", startTime, maxDelta)
                .collect(Collectors.toList());

        for (int i = 0; i < sites.size(); i++) {
            File ifile = new File("/home/ilan/mapnum" + i + ".html");
            Files.write(sites.get(i), new File("/home/ilan/mapnum" + i + ".html"), Charset.defaultCharset());
        }
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

