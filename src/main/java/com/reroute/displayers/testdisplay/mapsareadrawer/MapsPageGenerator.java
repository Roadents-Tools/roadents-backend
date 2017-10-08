package com.reroute.displayers.testdisplay.mapsareadrawer;

import com.google.common.collect.Lists;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;
import org.json.JSONArray;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 7/16/17.
 */
public class MapsPageGenerator {

    private static final Color[] COLORS = new Color[] {
            Color.BLUE,
            Color.MAGENTA,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.RED,
            Color.PINK,
            Color.ORANGE,
            Color.LIGHT_GRAY
    };
    private static String API_KEY = "AIzaSyB0pBdXuC4VRte73qnVtE5pLmxNs3ju0Gg";


    static AtomicLong count = new AtomicLong(0);
    public static Stream<String> generateIndividualPagesFromFile(String fileName, TimePoint startTime, TimeDelta maxDelta) {
        return readLatLngFromFile(fileName).stream()
                .peek(a -> LoggingUtils.logMessage("MapGenerator", "Now using latlng %s.", a.toString()))
                .map(latlng -> generateIndivitualPage(latlng, startTime, maxDelta));
    }

    public static List<StartPoint> readLatLngFromFile(String fileName) {
        try {
            return Files.lines(Paths.get(fileName))
                    .map(line -> line.split(","))
                    .peek(a -> LoggingUtils.logMessage("MapGenerator", "Now parsing %s.", Arrays.toString(a)))
                    .map(pair -> new double[] { Double.valueOf(pair[0]), Double.valueOf(pair[1]) })
                    .map(StartPoint::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyList();
        }
    }

    private static String generateIndivitualPage(StartPoint start, TimePoint startTime, TimeDelta maxDelta) {
        return generateCompositePage(Lists.newArrayList(start), startTime, maxDelta);
    }

    private static String generateCompositePage(List<StartPoint> starts, TimePoint startTime, TimeDelta maxDelta) {

        double latsum = 0;
        double lngsum = 0;
        for (StartPoint st : starts) {
            latsum += st.getCoordinates()[0];
            lngsum += st.getCoordinates()[1];
        }

        latsum /= starts.size();
        lngsum /= starts.size();

        LocationPoint centroid = new StartPoint(new double[] { latsum, lngsum });
        TimeDelta maxDistTime = starts.stream()
                .map(start -> LocationUtils.timeBetween(start, centroid))
                .max(Comparator.comparing(TimeDelta::getDeltaLong))
                .orElse(new TimeDelta(0));

        TimeDelta worldDelta = maxDelta.plus(maxDistTime);

        WorldInfo request = new WorldInfo.Builder()
                .setCenter(centroid)
                .setStartTime(startTime)
                .setMaxDelta(worldDelta)
                .build();
        StationRetriever.prepareWorld(request);

        TravelRouteJsonConverter conv = new TravelRouteJsonConverter();
        List<Map<LocationPoint, TimeDelta>> areas = starts.stream()
                .map(start -> LogicUtils.buildStationRouteList(start, startTime, maxDelta, null))
                .peek((LoggingUtils.WrappedConsumer<Set<TravelRoute>>) routes -> {
                    Path path = Paths.get("/home/ilan/output/routesnum" + count.getAndIncrement() + ".json");
                    Files.createFile(path);
                    JSONArray routesarr = new JSONArray(conv.toJson(routes));
                    Files.write(path, routesarr.toString(3).getBytes());
                })
                .map(routes -> routes.stream()
                        .collect(StreamUtils.collectWithMapping(TravelRoute::getCurrentEnd, route -> maxDelta.minus(route
                                .getTime()))))
                .collect(Collectors.toList());

        return generatePage(areas);
    }

    private static String generatePage(List<Map<LocationPoint, TimeDelta>> areas) {
        List<Color> colorMap = Arrays.asList(COLORS);
        if (colorMap.size() < areas.size()) {
            Random rng = new Random();
            for (int i = areas.size() - colorMap.size(); i >= 0; i--) {
                colorMap.add(new Color(rng.nextInt()));
            }
        }

        List<List<String>> circles = new ArrayList<>();
        for (int i = 0; i < areas.size(); i++) {
            List<String> areaCircles = new ArrayList<>();

            Color color = colorMap.get(i);
            Map<LocationPoint, TimeDelta> area = areas.get(i);
            for (LocationPoint pt : area.keySet()) {
                String name = pt.getName() + " " + i;
                double lat = pt.getCoordinates()[0];
                double lng = pt.getCoordinates()[1];
                double range = LocationUtils.timeToWalkDistance(area.get(pt)).inMeters();
                String colorString = Integer.toHexString(color.getRGB());

                String circle = String.format(MapsPageConstants.CIRCLE_FORMAT, name, lat, lng, range, colorString);
                areaCircles.add(circle);
            }

            circles.add(areaCircles);
        }

        StringJoiner circleJoiner = circles.stream()
                .flatMap(Collection::stream)
                .collect(() -> new StringJoiner(", \n"), StringJoiner::add, StringJoiner::merge);

        String javascript = String.format(MapsPageConstants.JS_FORMAT, circleJoiner.toString());

        return String.format(MapsPageConstants.HTML_FORMAT, javascript, API_KEY);
    }

    public static String generateCompositePageFromFile(String fileName, TimePoint startTime, TimeDelta maxDelta) {
        return generateCompositePage(readLatLngFromFile(fileName), startTime, maxDelta);
    }
}
