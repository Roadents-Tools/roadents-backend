package org.tymit.displayers.testdisplay.mapsareadrawer;

import org.tymit.projectdonut.logic.donutdisplay.DonutWalkMaximumTImeIndependentSupport;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
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


    public static Stream<String> generateIndividualPagesFromFile(String fileName, TimeDelta maxDelta) {
        return readLatLngFromFile(fileName)
                .peek(a -> LoggingUtils.logMessage("MapGenerator", "Now using latlng %s.", a.toString()))
                .map(latlng -> generateIndivitualPage(latlng, maxDelta));
    }

    public static Stream<StartPoint> readLatLngFromFile(String fileName) {
        try {
            return Files.lines(Paths.get(fileName))
                    .map(line -> line.split(","))
                    .peek(a -> LoggingUtils.logMessage("MapGenerator", "Now parsing %s.", Arrays.toString(a)))
                    .map(pair -> new double[] { Double.valueOf(pair[0]), Double.valueOf(pair[1]) })
                    .map(StartPoint::new);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Stream.empty();
        }
    }

    private static String generateIndivitualPage(StartPoint start, TimeDelta maxDelta) {
        return generateCompositePage(Stream.of(start), maxDelta);
    }

    private static String generateCompositePage(Stream<StartPoint> starts, TimeDelta maxDelta) {
        List<Map<LocationPoint, TimeDelta>> areas = starts
                .map(start -> DonutWalkMaximumTImeIndependentSupport.buildStationRouteList(start, maxDelta))
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
                double range = LocationUtils.timeToWalkDistance(area.get(pt).getDeltaLong(), false) * 1000;
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

    public static String generateCompositePageFromFile(String fileName, TimeDelta maxDelta) {
        return generateCompositePage(readLatLngFromFile(fileName), maxDelta);
    }
}
