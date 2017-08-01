package org.tymit;

import org.tymit.displayers.lambdacontroller.LambdaHandler;
import org.tymit.displayers.testdisplay.mapsareadrawer.MapsPageGenerator;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.postgresql.PostgresqlStationDbCache;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

        List<String> data = null;
        String outputFile = null;
        for (int i = 0; i < args.length; i++) {
            if ("--map".equals(args[i])) {
                mapLocations(args);
                return;
            }
            if ("-f".equals(args[i]) && args.length > i + 1) {
                String filePath = args[i + 1];
                data = new ArrayList<>(Files.readAllLines(Paths.get(filePath)));
            } else if ("-o".equals(args[i]) && args.length > i + 1) {
                outputFile = args[i + 1];
            }
        }

        if (data == null) {
            System.out.println("Couldn't read data.");
            return;
        }

        for (String input : data) {
            System.out.printf("Input: \n\n%s\n\n", input);
            ByteArrayInputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            LambdaHandler handler = new LambdaHandler();
            handler.handleRequest(stream, output, null);

            if (outputFile != null) {
                Files.write(Paths.get(outputFile), output.toByteArray());
            } else System.out.printf("Output: \n\n%s\n\n", output.toString("utf-8"));
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

    private static void mapLocations(String[] args) {
        TimePoint startTime = new TimePoint(1500829200 * 1000L, "America/New_York");
        TimeDelta maxDelta = new TimeDelta(1800 * 1000);
        String outputDir = "~";
        String filePath = null;

        for (int i = 0; i < args.length; i++) {
            if ("-f".equals(args[i]) && args.length > i + 1) {
                filePath = args[i + 1];
            } else if ("-o".equals(args[i]) && args.length > i + 1) {
                outputDir = args[i + 1];
            }
        }

        if (filePath == null) LoggingUtils.logError("ScratchRunner", "Need an input to run.");
        mapLocations(filePath, startTime, maxDelta, outputDir);
    }

    private static void mapLocations(String file, TimePoint startTime, TimeDelta maxDelta, String outputDir) {
        AtomicLong count = new AtomicLong(0);
        MapsPageGenerator.generateIndividualPagesFromFile(file, startTime, maxDelta)
                .forEach((LoggingUtils.WrappedConsumer<String>) pg -> {
                    String filename = "mapnum" + count.getAndIncrement() + ".html";
                    String path = outputDir + "/" + filename;
                    Files.createFile(Paths.get(path));
                    Files.write(Paths.get(path), pg.getBytes());
                });

    }

}

