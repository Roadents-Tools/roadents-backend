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
import com.reroute.backend.logic.pitch.PitchCore;
import com.reroute.backend.logic.pitch.PitchSorter;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.logic.utils.StationRoutesBuildRequest;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.gtfs.GtfsProvider;
import com.reroute.backend.stations.postgresql.PostgresqlDonutDb;
import com.reroute.backend.stations.redis.RedisDonutCache;
import com.reroute.backend.stations.transitland.TransitlandApiDb;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;
import com.reroute.displayers.lambdacontroller.LambdaHandler;
import com.reroute.displayers.restcontroller.SparkHandler;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) throws Exception {

        LoggingUtils.setPrintImmediate(true);
        try {
            for (String arg : args) {
                if ("--cache".equals(arg)) {
                    cache(args);
                    return;
                }
                if ("--spark".equals(arg)) {
                    runSpark(args);
                    return;
                }
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

                if ("--default".equals(arg)) {
                    runFinder(args);
                    return;
                }
            }

        } catch (Exception e) {
            LoggingUtils.logError(e);
        }


    }

    private static void runSpark(String[] args) {
        SparkHandler.main(args);
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
        TimePoint startTime = TimePoint.from(1500829200 * 1000L, "America/New_York");
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
                    TimeDelta timeDiff = aRoute.getTime().minus(bRoute.getTime());

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

    private static void testDonutLimits(String[] args) {

        //TODO: Extract args
        StartPoint startPoint = new StartPoint(40.7570, -73.9718);
        TimePoint startTime = TimePoint.from(1500829200 * 1000L, "America/New_York");
        TimeDelta maxDelta = new TimeDelta(30 * 60 * 1000);

        Map<String, StationRoutesBuildRequest> reqToTags = new HashMap<>();
        StationRoutesBuildRequest simple = new StationRoutesBuildRequest(startPoint, startTime, maxDelta);
        /*reqToTags.put(
                "simple", simple
        );*/
        /*reqToTags.put(
                "layerFilter: traveldistTotal <= 5 * maxWalkDist", simple.withLayerFilter(rt -> {
                    Distance maxDist = LocationUtils.timeToWalkDistance(maxDelta);
                    List<TravelRouteNode> route = rt.getRoute();

                    Distance totalTravelled = Distance.NULL;
                    for (int i = 1; i < route.size(); i++) {
                        Distance d = LocationUtils.distanceBetween(route.get(i).getPt(), route.get(i-1).getPt());
                        totalTravelled = totalTravelled.plus(d);
                    }
                    return totalTravelled.inMeters() <= maxDist.inMeters();
                })
        );
        reqToTags.put(
                "layerFilter: currentEndDist <= 5 * maxWalkDist", simple.withLayerFilter(rt -> {
                    Distance maxDist = LocationUtils.timeToWalkDistance(maxDelta);
                    return LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters() <= maxDist.inMeters();
                })
        );
        reqToTags.put(
                "layerFilter: walktimeTotal <= .5*maxTime", simple.withLayerFilter(rt -> rt.getWalkTime().getDeltaLong() <= .5 * maxDelta.getDeltaLong())
        );
        //TODO: Run this version of this filter test
        */
        reqToTags.put("simple", simple);
        reqToTags.put(
                "layerFilter: walktimeBigger @ 100", simple.withLayerFilter(rt -> {
                    int totalWalks = 1 + (int) rt.getRoute().stream()
                            .filter(TravelRouteNode::arrivesByFoot)
                            .count();
                    double maxPercent = IntStream.range(1, totalWalks)
                            .mapToDouble(a -> 1. / Math.pow(2, a))
                            .sum();
                    return rt.getWalkTime().getDeltaLong() <= maxPercent * maxDelta.getDeltaLong();
                })
                        .withLayerLimit(100)
        );
        reqToTags.put(
                "layerFilter: walktimeBigger @ 200", simple.withLayerFilter(rt -> {
                    int totalWalks = 1 + (int) rt.getRoute().stream()
                            .filter(TravelRouteNode::arrivesByFoot)
                            .count();
                    double maxPercent = IntStream.range(1, totalWalks)
                            .mapToDouble(a -> 1. / Math.pow(2, a))
                            .sum();
                    return rt.getWalkTime().getDeltaLong() <= maxPercent * maxDelta.getDeltaLong();
                })
                        .withLayerLimit(200)
        );
        reqToTags.put(
                "layerFilter: walktimeBigger @ 500", simple.withLayerFilter(rt -> {
                    int totalWalks = 1 + (int) rt.getRoute().stream()
                            .filter(TravelRouteNode::arrivesByFoot)
                            .count();
                    double maxPercent = IntStream.range(1, totalWalks)
                            .mapToDouble(a -> 1. / Math.pow(2, a))
                            .sum();
                    return rt.getWalkTime().getDeltaLong() <= maxPercent * maxDelta.getDeltaLong();
                })
                        .withLayerLimit(500)
        );
        /*
        reqToTags.put(
                "layerFilter: routetimeTotal = sum(i = 0 -> layercount, 1/2^i", simple.withLayerFilter(rt -> {
                    int totalLayers = rt.getRoute().size() -1;
                    double maxPercent = IntStream.range(1, totalLayers)
                            .mapToDouble(a -> 1./Math.pow(2, a))
                            .sum();
                    return  rt.getPackedTime().getDeltaLong() <= maxPercent * maxDelta.getDeltaLong();
                })
        );*/

        LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Starting donut tests.");
        LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Starting donut test executions.");
        reqToTags.forEach((tag, request) -> {
            LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Starting execution of %s.", tag);
            Set<TravelRoute> routes = LogicUtils.buildStationRouteList(request);
            LoggingUtils.logMessage("DONUT_FILTER_TESTER", "%s got %d routes.", tag, routes.size());

            double maxDist = -1;
            Optional<TravelRoute> maxRoute = Optional.empty();
            double minDist = -1;
            Optional<TravelRoute> minRoute = Optional.empty();
            int maxNode = 1;
            Optional<TravelRoute> nodeRoute = Optional.empty();
            for (TravelRoute rt : routes) {
                if (rt.getRoute().size() <= 1) continue;

                double distance = LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters();
                if (distance > maxDist) {
                    maxDist = distance;
                    maxRoute = Optional.of(rt);
                } else if (distance < minDist || minDist == -1) {
                    minDist = distance;
                    minRoute = Optional.of(rt);
                }

                int nodeCount = rt.getRoute().size();
                if (nodeCount > maxNode) {
                    maxNode = nodeCount;
                    nodeRoute = Optional.of(rt);
                }

            }

            LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Max distance is %f, from route\n%s\n", maxDist, maxRoute.map(TravelRoute::toString)
                    .orElse("NULL"));
            LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Min distance is %f, from route\n%s\n", minDist, minRoute.map(TravelRoute::toString)
                    .orElse("NULL"));
            LoggingUtils.logMessage("DONUT_FILTER_TESTER", "Max node len is %d, from route\n%s\n", maxNode, nodeRoute.map(TravelRoute::toString)
                    .orElse("NULL"));
        });


    }

    private static void testPitch(String[] args) {

        StartPoint startPoint = new StartPoint(40.7570, -73.9718);
        TimePoint startTime = TimePoint.from(1500829200 * 1000L, "America/New_York");
        TimeDelta maxDelta = new TimeDelta(30 * 60 * 1000);
        LocationType query = new LocationType("Food", "Food");

        ApplicationRequest req = new ApplicationRequest.Builder(PitchCore.TAG)
                .withStartPoint(startPoint)
                .withMaxDelta(maxDelta)
                .withStartTime(startTime)
                .withQuery(query)
                .build();

        LoggingUtils.logMessage("SCRATCH", "Starting pitch.");
        ApplicationResult res = ApplicationRunner.runApplication(req);
        if (res.hasErrors()) {
            res.getErrors().forEach(LoggingUtils::logError);
        }
        String output = new TravelRouteJsonConverter().toJson(res.getResult());
        LoggingUtils.logMessage("SCRATCH", "Finished pitch.");
        System.out.println(new TravelRouteJsonConverter().toJson(res.getResult()));
    }

    private static void cache(String[] args) {
        RedisDonutCache cache = new RedisDonutCache("debstop.dynamic.ucsd.edu", 6379);
        PostgresqlDonutDb db = new PostgresqlDonutDb(PostgresqlDonutDb.DB_URLS[0]);
        WorldInfo req = new WorldInfo.Builder()
                .setCenter(new StartPoint(40.676843, -74.041512))
                .setRange(new Distance(1000, DistanceUnits.KILOMETERS))
                .setStartTime(TimePoint.NULL)
                .setMaxDelta(new TimeDelta(Long.MAX_VALUE))
                .build();
        cache.putWorld(req, db.getWorld(req.getCenter(), req.getRange(), req.getStartTime(), req.getMaxDelta()));
    }

    private static void analyzeJson(String[] args) throws IOException {
        String file = args[args.length - 1];
        String json = Files.lines(Paths.get(file)).collect(StreamUtils.joinToString("\n"));
        List<TravelRoute> routes = new ArrayList<>(120);
        new TravelRouteJsonConverter().fromJson(json, routes);

        int size = routes.size();
        int sortSize = PitchSorter.values().length;
        int perSort = size / sortSize;
        LoggingUtils.logMessage("SCRATCH", "Size info: %d, %d, %d", size, sortSize, perSort);

        Set<TravelRoute> deduped = new HashSet<>(routes);
        LoggingUtils.logMessage("SCRATCH", "Dupes: %d", size - deduped.size());

        double minDist = routes.stream()
                .mapToDouble(rt -> LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters())
                .min()
                .orElse(-1);

        double maxDist = routes.stream()
                .mapToDouble(rt -> LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "Dist info: %f, %f", minDist, maxDist);

        double minDisp = routes.stream()
                .mapToDouble(rt -> rt.getDisp().inMeters())
                .min()
                .orElse(-1);

        double maxDisp = routes.stream()
                .mapToDouble(rt -> rt.getDisp().inMeters())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "Disp info: %f, %f", minDisp, maxDisp);

        double minWalkDisp = routes.stream()
                .mapToDouble(rt -> rt.getWalkDisp().inMeters())
                .min()
                .orElse(-1);

        double maxWalkDisp = routes.stream()
                .mapToDouble(rt -> rt.getWalkDisp().inMeters())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "WalkDisp info: %f, %f", minWalkDisp, maxWalkDisp);

        long minTime = routes.stream()
                .mapToLong(rt -> rt.getTime().getDeltaLong())
                .min()
                .orElse(-1);

        long maxTime = routes.stream()
                .mapToLong(rt -> rt.getTime().getDeltaLong())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "Time info: %d, %d", minTime, maxTime);

        int minLen = routes.stream()
                .mapToInt(rt -> rt.getRoute().size())
                .min()
                .orElse(-1);

        int maxLen = routes.stream()
                .mapToInt(rt -> rt.getRoute().size())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "Len info: %d, %d", minLen, maxLen);


        double minEndSpeed = routes.stream()
                .mapToDouble(rt -> LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd())
                        .inKilometers() / rt.getTime().inHours())
                .min()
                .orElse(-1);

        double maxEndSpeed = routes.stream()
                .mapToDouble(rt -> LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd())
                        .inKilometers() / rt.getTime().inHours())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "EndSpeed info: %f, %f", minEndSpeed, maxEndSpeed);

        double minAvgVel = routes.stream()
                .mapToDouble(rt -> rt.getDisp().inKilometers() / rt.getTime().inHours())
                .min()
                .orElse(-1);

        double maxAvgVel = routes.stream()
                .mapToDouble(rt -> rt.getDisp().inKilometers() / rt.getTime().inHours())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "AvgVel info: %f, %f", minAvgVel, maxAvgVel);

        Set<TravelRoute> weirdEnd = routes.stream()
                .filter(rt -> !rt.getCurrentEnd().equals(rt.getDestination()))
                .collect(Collectors.toSet());
        LoggingUtils.logMessage("SCRATCH", "WeirdEnd: %d", weirdEnd.size());

        Set<TravelRoute> defDumbRoutes = routes.stream()
                .filter(rt -> rt.getRoute().size() > 2)
                .filter(rt -> rt.getTime()
                        .getDeltaLong() > 1000 + LocationUtils.timeBetween(rt.getStart(), rt.getCurrentEnd())
                        .getDeltaLong())
                .collect(Collectors.toSet());
        LoggingUtils.logMessage("SCRATCH", "Dumb: %d", defDumbRoutes.size());
        long minDumb = defDumbRoutes.stream()
                .mapToLong(rt -> rt.getTime()
                        .getDeltaLong() - LocationUtils.timeBetween(rt.getStart(), rt.getCurrentEnd()).getDeltaLong())
                .min()
                .orElse(-1);
        long maxDumb = defDumbRoutes.stream()
                .mapToLong(rt -> rt.getTime()
                        .getDeltaLong() - LocationUtils.timeBetween(rt.getStart(), rt.getCurrentEnd()).getDeltaLong())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "Dumb info: %f, %f", minDumb * 1. / (1000 * 60), maxDumb * 1. / (60 * 1000));

        Set<TravelRoute> veryDumbRoutes = defDumbRoutes.stream()
                .filter(rt -> rt.getWalkDisp()
                        .inMeters() > 10 + LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters())
                .collect(Collectors.toSet());
        LoggingUtils.logMessage("SCRATCH", "Very dumb routes: %d", veryDumbRoutes.size());

        double minVeryDumb = veryDumbRoutes.stream()
                .mapToDouble(rt -> rt.getWalkDisp()
                        .inMeters() - LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters())
                .min()
                .orElse(-1);
        double maxVeryDumb = veryDumbRoutes.stream()
                .mapToDouble(rt -> rt.getWalkDisp()
                        .inMeters() - LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd()).inMeters())
                .max()
                .orElse(-1);
        LoggingUtils.logMessage("SCRATCH", "VeryDumb info: %f, %f", minVeryDumb, maxVeryDumb);

        Set<TravelRoute> dumbByFilter = routes.stream()
                .filter(PitchCore.isntDumbRoute.negate())
                .collect(Collectors.toSet());
        LoggingUtils.logMessage("SCRATCH", "Filtered dumb routes: %d", dumbByFilter.size());

        Map<Integer, List<Integer>> dupes = new HashMap<>();
        for (int i = 0; i < routes.size(); i++) {
            for (int j = 0; j < routes.size(); j++) {
                if (i == j) continue;
                if (routes.get(i).equals(routes.get(j))) {
                    dupes.computeIfAbsent(i, unused -> new ArrayList<>()).add(j);
                }
            }
        }

        for (int i = 0; i < PitchSorter.values().length; i++) {
            PitchSorter cursort = PitchSorter.values()[i];
            int listInd = i * perSort;
            List<TravelRoute> curlist = routes.subList(listInd, listInd + perSort);
            LoggingUtils.logMessage("SCRATCH", "Current sorter is %s.", cursort.getTag());
            for (int i1 = 0; i1 < perSort; i1++) {
                TravelRoute rt = curlist.get(i1);
                LoggingUtils.logMessage("SCRATCH", "Route dest: %s (%s), Time: %f, Dist: %f, Disp: %f, Len: %d",
                        rt.getDestination().getName(), rt.getDestination().getAddress().orElse("NULL"),
                        rt.getTime().inMinutes(), LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd())
                                .inMeters(), rt.getDisp().inMeters(),
                        rt.getRoute().size());

                List<Integer> dupeInds = dupes.get(listInd + i1);
                if (dupeInds == null || dupeInds.isEmpty()) continue;
                for (int ind : dupeInds) {
                    PitchSorter dupeSort = PitchSorter.values()[ind / perSort];
                    int offset = ind % PitchSorter.values().length;
                    LoggingUtils.logMessage("SCRATCH", "Is duplicated at %s number %d", dupeSort.getTag(), offset);
                }
            }
        }

    }
}

