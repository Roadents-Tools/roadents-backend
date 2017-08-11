package com.reroute;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.moodysalem.TimezoneMapper;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.logic.calculator.CalculatorCore;
import com.reroute.backend.logic.generator.GeneratorCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.gtfs.GtfsProvider;
import com.reroute.backend.stations.postgresql.PostgresqlDonutDb;
import com.reroute.backend.stations.transitland.TransitlandApiDb;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;
import com.reroute.displayers.lambdacontroller.LambdaHandler;
import com.reroute.displayers.testdisplay.maproutedrawer.RouteMapsPageGenerator;
import com.reroute.displayers.testdisplay.mapsareadrawer.MapsPageGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);

        try {
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

                if ("--dr".equals(arg)) {
                    mapRoutes(args);
                    return;
                }

                if ("--finder".equals(arg)) {
                    finderMaxDist(args);
                    return;
                }

                if ("--calc".equals(arg)) {
                    calculatorMap(args);
                    return;
                }
            }

            runFinder(args);
        } catch (Exception e) {
            LoggingUtils.logError(e);
        }


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
        TimeDelta maxDelta = new TimeDelta(900 * 1000);
        String outputDir = "~";
        String filePath = null;

        for (int i = 0; i < args.length; i++) {
            if ("-f".equals(args[i]) && args.length > i + 1) {
                filePath = args[i + 1];
            } else if ("-o".equals(args[i]) && args.length > i + 1) {
                outputDir = args[i + 1];
            } else if ("-dt".equals(args[i]) && args.length > i + 1) {
                maxDelta = new TimeDelta(1000 * Long.parseLong(args[i + 1]));
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

    private static void mapRoutes(String[] args) throws IOException {
        String outputDir = "/home/ilan";
        String filePath = null;
        boolean composite = false;

        for (int i = 0; i < args.length; i++) {
            if ("-f".equals(args[i]) && args.length > i + 1) {
                filePath = args[i + 1];
            } else if ("-o".equals(args[i]) && args.length > i + 1) {
                outputDir = args[i + 1];
            } else if ("-c".equals(args[i])) {
                composite = true;
            }
        }

        if (filePath == null) LoggingUtils.logError("ScratchRunner", "Need an input to run.");
        AtomicLong count = new AtomicLong(0);
        String finalOutputDir = outputDir;
        if (composite) {
            String page = RouteMapsPageGenerator.generateCompositePageFromFile(filePath);
            String fileName = outputDir + "/composite.html";
            Path path = Paths.get(fileName);
            Files.createFile(path);
            Files.write(path, page.getBytes());
        } else {
            RouteMapsPageGenerator.generateIndividualPagesFromFile(filePath)
                    .forEach((LoggingUtils.WrappedConsumer<String>) pg -> {
                        String filename = "mapnum" + count.getAndIncrement() + ".html";
                        String path = finalOutputDir + "/" + filename;
                        Files.createFile(Paths.get(path));
                        Files.write(Paths.get(path), pg.getBytes());
                    });
        }
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
                .forEach(url -> {
                    String result;

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
                        result = rval;
                    } catch (Exception e) {
                        LoggingUtils.logError(e);
                        result = null;
                    }
                });
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

    private static void finderMaxDist(String[] args) {
        StartPoint starta = null;
        StartPoint startb = null;
        LocationType query = null;
        TimePoint startTime = TimePoint.NULL;
        TimeDelta maxDelta = TimeDelta.NULL;
        String fileName = null;

        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && args.length > i + 1) {
                fileName = args[i + 1];
            }
            if ("-s".equals(args[i]) && args.length > i + 1) {
                String[] ab = args[i + 1].split(";");
                String[] alatlngStr = ab[0].split(",");
                starta = new StartPoint(new double[] {
                        Double.parseDouble(alatlngStr[0]),
                        Double.parseDouble(alatlngStr[1])
                });
                String[] blatlngStr = ab[1].split(",");
                startb = new StartPoint(new double[] {
                        Double.parseDouble(blatlngStr[0]),
                        Double.parseDouble(blatlngStr[1])
                });
            }
            if ("-d".equals(args[i]) && args.length > i + 1) {
                query = new LocationType(args[i + 1], args[i + 1]);
            }
            if ("-t".equals(args[i]) && args.length > i + 1) {
                String[] timeParts = args[i + 1].split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int min = Integer.parseInt(timeParts[1]);
                int sec = Integer.parseInt(timeParts[2]);
                startTime = TimePoint.now(starta != null ? TimezoneMapper.tzNameAt(starta.getCoordinates()[0], starta.getCoordinates()[1]) : "GMT")
                        .withHour(hour)
                        .withMinute(min)
                        .withSecond(sec);
            }
            if ("-dt".equals(args[i]) && args.length > i + 1) {
                long dt = 1000L * Long.parseLong(args[i + 1]);
                maxDelta = new TimeDelta(dt);
            }
        }

        ApplicationRequest.Builder genDonut = new ApplicationRequest.Builder(GeneratorCore.TAG)
                .withStartTime(startTime)
                .withQuery(query)
                .withMaxDelta(maxDelta)
                .withFilter(LogicUtils.isRouteInRange(starta, maxDelta)
                        .and(LogicUtils.isRouteInRange(startb, maxDelta)));

        ApplicationRequest aDonut = genDonut.withStartPoint(starta).build();
        Map<DestinationLocation, TravelRoute> aRoutes = ApplicationRunner.runApplication(aDonut)
                .getResult().stream()
                .collect(StreamUtils.collectWithKeys(TravelRoute::getDestination));

        ApplicationRequest bDonut = genDonut.withStartPoint(startb).build();
        Map<DestinationLocation, TravelRoute> bRoutes = ApplicationRunner.runApplication(bDonut)
                .getResult().stream()
                .collect(StreamUtils.collectWithKeys(TravelRoute::getDestination));

        Set<DestinationLocation> bothDests = Sets.intersection(aRoutes.keySet(), bRoutes.keySet());

        StartPoint finalStartb1 = startb;
        StartPoint finalStarta1 = starta;
        Optional<DestinationLocation> biggestDistDiffOpt = bothDests.stream()
                .filter(dest -> aRoutes.get(dest).getRoute().size() > 2 && bRoutes.get(dest).getRoute().size() > 2)
                .max(Comparator.comparing(dest -> {
                    TravelRoute aRoute = aRoutes.get(dest);
                    TravelRoute bRoute = bRoutes.get(dest);

                    double distDiff = LocationUtils.distanceBetween(dest, finalStarta1)
                            .inMeters() - LocationUtils.distanceBetween(dest, finalStartb1).inMeters();
                    TimeDelta timeDiff = aRoute.getTotalTime().minus(bRoute.getTotalTime());

                    return distDiff - 100 * timeDiff.getDeltaLong();
                }));

        if (!biggestDistDiffOpt.isPresent()) {
            LoggingUtils.logError("ScratchRunner", "Got no Finder routes.");
            throw new RuntimeException();
        }

        DestinationLocation dest = biggestDistDiffOpt.get();
        String json = new TravelRouteJsonConverter().toJson(Lists.newArrayList(aRoutes.get(dest), bRoutes.get(dest)));

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

    private static void calculatorMap(String[] args) {
        StartPoint starta = null;
        DestinationLocation startb = null;
        LocationType query = null;
        TimePoint startTime = TimePoint.NULL;
        TimeDelta maxDelta = TimeDelta.NULL;
        String fileName = null;

        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && args.length > i + 1) {
                fileName = args[i + 1];
            }
            if ("-s".equals(args[i]) && args.length > i + 1) {
                String[] ab = args[i + 1].split(";");
                String[] alatlngStr = ab[0].split(",");
                starta = new StartPoint(new double[] {
                        Double.parseDouble(alatlngStr[0]),
                        Double.parseDouble(alatlngStr[1])
                });
                String[] blatlngStr = ab[1].split(",");
                startb = new DestinationLocation("B", new LocationType("B Loc", "B Loc"),
                        new double[] {
                                Double.parseDouble(blatlngStr[0]),
                                Double.parseDouble(blatlngStr[1])
                        });
            }
            if ("-d".equals(args[i]) && args.length > i + 1) {
                query = new LocationType(args[i + 1], args[i + 1]);
            }
            if ("-t".equals(args[i]) && args.length > i + 1) {
                String[] timeParts = args[i + 1].split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int min = Integer.parseInt(timeParts[1]);
                int sec = Integer.parseInt(timeParts[2]);
                startTime = TimePoint.now(starta != null ? TimezoneMapper.tzNameAt(starta.getCoordinates()[0], starta.getCoordinates()[1]) : "GMT")
                        .withHour(hour)
                        .withMinute(min)
                        .withSecond(sec);
            }
            if ("-dt".equals(args[i]) && args.length > i + 1) {
                long dt = 1000L * Long.parseLong(args[i + 1]);
                maxDelta = new TimeDelta(dt);
            }
        }

        ApplicationRequest finderRequest = new ApplicationRequest.Builder(CalculatorCore.TAG)
                .withStartPoint(starta)
                .withEndPoint(startb)
                .withStartTime(startTime)
                .withMaxDelta(maxDelta)
                .withQuery(query)
                .build();

        ApplicationResult res = ApplicationRunner.runApplication(finderRequest);


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

