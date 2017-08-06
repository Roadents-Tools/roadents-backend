package com.reroute;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.gtfs.GtfsProvider;
import com.reroute.backend.stations.postgresql.PostgresqlDonutDb;
import com.reroute.backend.stations.transitland.TransitlandApiDb;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.TimeUtils;
import com.reroute.displayers.lambdacontroller.LambdaHandler;
import com.reroute.displayers.testdisplay.mapsareadrawer.MapsPageGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            if ("--urls".equals(args[i])) {
                listUrls(args);
                return;
            }
	    if("--load".equals(args[i]))  {
	    	loadtransitzips(args);
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

    private static void mapLocations(String[] args) {
        TimePoint startTime = new TimePoint(1500829200 * 1000L, "America/New_York");
        TimeDelta maxDelta = new TimeDelta(3600 * 1000);
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

    private static void listUrls(String[] args) {
        Distance range = new Distance(1, DistanceUnits.METERS);
        Double lat = null;
        Double lng = null;


        for (int i = 0; i < args.length; i++) {
            if ("-lat".equals(args[i]) && args.length > i + 1) {
                lat = Double.parseDouble(args[i + 1]);
            } else if ("-lng".equals(args[i]) && args.length > i + 1) {
                lng = Double.parseDouble(args[i + 1]);
            } else if("-d".equals(args[i]) && args.length > i + 1) {
	    	range = new Distance(Double.parseDouble(args[i + 1]), DistanceUnits.METERS);
	    }
        }

        if (lat == null || lng == null) {
            LoggingUtils.logError("ScratchRunner", "Coords not passed correctly.");
            return;
        }

        StartPoint center = new StartPoint(new double[] { lat, lng });
        listUrls(center, range);
    }

    private static void listUrls(LocationPoint center, Distance range) {
        TransitlandApiDb apidb = new TransitlandApiDb();
        Map<String, String> skipBad = new HashMap<>();
	skipBad.put("license_use_without_attribution", "no");
	apidb.getFeedsInArea(center, range, null, skipBad).stream()
                .peek(System.out::println)
                .forEach(ScratchRunner::dlZips);
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

    private static boolean checkZip(String file, TransChain toFind) {
        if (file == null || toFind == null) return false;

        GtfsProvider prov = new GtfsProvider(file);

        Map<TransChain, List<TransStation>> inProv = prov.getUpdatedStations();
        Map.Entry<TransChain, List<TransStation>> foundEntry = inProv.entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(toFind.getName()))
                .findAny()
                .orElse(null);

        if (foundEntry == null) return false;
        System.out.printf(" Found %s in %s.\n", toFind.getName(), file);
        System.out.printf(" Stations: \n");
        int bound = foundEntry.getValue().size();
        foundEntry.getValue().sort(Comparator.comparingLong(o -> TimeUtils.packSchedulePoint(o.getSchedule().get(0))));
        for (int i = 0; i < bound; i++) {
            if (i > 0) {
                TransStation cur = foundEntry.getValue().get(i);
                long curPackedSched = TimeUtils.packSchedulePoint(cur.getSchedule().get(0));
                TransStation prev = foundEntry.getValue().get(i - 1);
                long prevPackedSched = TimeUtils.packSchedulePoint(prev.getSchedule().get(0));
                System.out.printf("      Dist: %f meters, %d seconds, %f mph\n\n",
                        LocationUtils.distanceBetween(cur, prev).inMeters(),
                        curPackedSched - prevPackedSched,
                        LocationUtils.distanceBetween(cur, prev)
                                .inMiles() * 60 * 60 / (curPackedSched - prevPackedSched));
            }
            System.out.printf("  S%d: %s\n\n", i, foundEntry.getValue().get(i).toString());
        }
        return true;
    }

    private static void loadtransitzips(String[] args) {
        String dburl = PostgresqlDonutDb.DB_URLS[1];
        String rootdir = null;
        for (int i = 0; i < args.length; i++) {
            if ("-d".equals(args[i]) && args.length > i + 1) {
                rootdir = args[i + 1];
            }
            if ("-url".equals(args[i]) && args.length > i + 1) {
                dburl = args[i + 1];
            }
        }

        if (dburl == null || rootdir == null) {
            LoggingUtils.logError("ScratchRunner", "Got null arguments for database populator. Returning.");
            return;
        }

        loadtransitzips(rootdir, dburl);
    }

    private static boolean loadtransitzips(String rootdirectory, String dburl) {
        PostgresqlDonutDb db = new PostgresqlDonutDb(dburl);
        File rootFile = new File(rootdirectory);
        return Arrays.stream(rootFile.listFiles()).parallel().allMatch(file -> {
            LoggingUtils.logMessage("DB Loader", "Starting URL %s.", file.getName());
            Map<TransChain, List<TransStation>> mp = new GtfsProvider(file).getUpdatedStations();
            if (!mp.values().stream().allMatch(db::putStations)) {
                LoggingUtils.logError("DB Loader", "ERR on URL %s.", file.getName());
                return false;
            }
            LoggingUtils.logMessage("DB Loader", "Finished URL %s.", file.getName());
            return true;
        });
    }

    private static String dlZips(URL url) {

        String rval = "/home/main/Downloads/tzip/" + url.getFile().replaceAll("/", "__");

        File zipFile = new File(rval);
        try {
            zipFile.delete();
            zipFile.createNewFile();
            zipFile.setWritable(true);
            URLConnection con = url.openConnection();
            URL trurl = con.getHeaderField("Location") == null
                    ? url
                    : new URL(con.getHeaderField("Location"));
            Files.copy(trurl.openStream(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return rval;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return null;
        }
    }

}
