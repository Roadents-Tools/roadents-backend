package org.tymit;

import org.tymit.displayers.lambdacontroller.LambdaHandler;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.postgresql.PostgresqlStationDbCache;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

        String data = null;
        for (int i = 0; i < args.length; i++) {
            if ("-f".equals(args[i]) && args.length > i + 1) {
                String filePath = args[i + 1];
                data = Files.readAllLines(Paths.get(filePath)).stream()
                        .collect(StringBuilder::new, StringBuilder::append, (r, r2) -> r.append(r2.toString()))
                        .toString();

                break;
            }
        }

        if (data == null) {
            System.out.println("Couldn't read data.");
            return;
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LambdaHandler handler = new LambdaHandler();
        handler.handleRequest(stream, output, null);

        System.out.printf("Output: \n\n%s\n\n", output.toString("utf-8"));
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

