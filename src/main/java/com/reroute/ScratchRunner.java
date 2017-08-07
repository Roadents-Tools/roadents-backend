package com.reroute;

import com.moodysalem.TimezoneMapper;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
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
import java.io.IOException;
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
import java.util.stream.Collectors;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

        for (String arg : args) {
            if ("--map".equals(arg)) {
                mapLocations(args);
                return;
            }
            if ("--urls".equals(arg)) {
                listUrls(args);
                return;
            }
            if ("--load".equals(arg)) {
                loadtransitzips(args);
                return;
            }

            if ("--route".equals(arg)) {
                generateBestRoutes(args);
                return;
            }
        }

        runFinder(args);

    }

    private static void runFinder(String[] args) throws IOException {

        List<String> data = null;
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {

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
        AtomicLong count = new AtomicLong(0);
        String finalOutputDir = outputDir;
        MapsPageGenerator.generateIndividualPagesFromFile(filePath, startTime, maxDelta)
                .forEach((LoggingUtils.WrappedConsumer<String>) pg -> {
                    String filename = "mapnum" + count.getAndIncrement() + ".html";
                    String path = finalOutputDir + "/" + filename;
                    Files.createFile(Paths.get(path));
                    Files.write(Paths.get(path), pg.getBytes());
                });

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
        TransitlandApiDb apidb = new TransitlandApiDb();
        Map<String, String> skipBad = new HashMap<>();
        skipBad.put("license_use_without_attribution", "no");
        apidb.getFeedsInArea(center, range, null, skipBad).stream()
                .peek(System.out::println)
                .forEach(ScratchRunner::dlZips);
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

        PostgresqlDonutDb db = new PostgresqlDonutDb(dburl);
        File rootFile = new File(rootdir);
        Arrays.stream(rootFile.listFiles()).parallel().allMatch(file -> {
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

    private static void generateBestRoutes(String[] args) {
        String fileName = null;
        StartPoint startPt = null;
        List<DestinationLocation> endPts = null;
        int hour = -1;
        int min = -1;
        int sec = -1;
        TimeDelta dt = TimeDelta.NULL;

        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && args.length > i + 1) {
                fileName = args[i + 1];
            }
            if ("-s".equals(args[i]) && args.length > i + 1) {
                String[] latlngStr = args[i + 1].split(",");
                startPt = new StartPoint(new double[] {
                        Double.parseDouble(latlngStr[0]),
                        Double.parseDouble(latlngStr[1])
                });
            }
            if ("-d".equals(args[i]) && args.length > i + 1) {
                String[] dests = args[i + 1].split(";");
                endPts = Arrays.stream(dests)
                        .map(dest -> dest.split(","))
                        .map(coordsStr -> new double[] { Double.parseDouble(coordsStr[0]), Double.parseDouble(coordsStr[1]) })
                        .map(coords -> new DestinationLocation(
                                String.format("D at %f, %f", coords[0], coords[1]),
                                new LocationType("Dest", "Dest"),
                                coords
                        ))
                        .collect(Collectors.toList());
            }
            if ("-t".equals(args[i]) && args.length > i + 1) {
                String[] timeParts = args[i + 1].split(":");
                hour = Integer.parseInt(timeParts[0]);
                min = Integer.parseInt(timeParts[1]);
                sec = Integer.parseInt(timeParts[2]);
            }
            if ("-dt".equals(args[i]) && args.length > i + 1) {
                long maxDelta = 1000L * Long.parseLong(args[i + 1]);
                dt = new TimeDelta(maxDelta);
            }
        }

        TimePoint startTime = TimePoint.now(TimezoneMapper.tzNameAt(startPt.getCoordinates()[0], startPt.getCoordinates()[1]))
                .withHour(hour)
                .withMinute(min)
                .withSecond(sec);

        ApplicationRequest request = new ApplicationRequest.Builder("DONUTAB_BEST")
                .withStartTime(startTime)
                .withEndPoints(endPts)
                .withStartPoint(startPt)
                .withMaxDelta(dt)
                .build();

        LoggingUtils.logMessage("Scratch-Routing", "Request: %s", request.toString());

        ApplicationResult res = ApplicationRunner.runApplication(request);
        if (res.hasErrors()) {
            res.getErrors().forEach(LoggingUtils::logError);
        }
        String json = new TravelRouteJsonConverter().toJson(res.getResult());

        if (fileName == null) {
            LoggingUtils.logMessage("ScratchRunner", "Got routes:\n\n%s\n\n", json);
        } else {
            try {
                Files.write(Paths.get(fileName), json.getBytes());
            } catch (IOException e) {
                LoggingUtils.logError(e);
            }
        }

    }
}

