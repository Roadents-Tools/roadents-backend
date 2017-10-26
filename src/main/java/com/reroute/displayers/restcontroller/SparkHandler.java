package com.reroute.displayers.restcontroller;

import com.moodysalem.TimezoneMapper;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;
import spark.Spark;

import java.util.Map;
import java.util.stream.Collectors;

public class SparkHandler {

    private static final String START_TIME_TAG = "starttime";
    private static final String LAT_TAG = "latitude";
    private static final String LONG_TAG = "longitude";
    private static final String TYPE_TAG = "type";
    private static final String TIME_DELTA_TAG = "timedelta";
    private static final String TEST_TAG = "test";

    public static void main(String[] args) {
        TravelRouteJsonConverter converter = new TravelRouteJsonConverter();
        LoggingUtils.setPrintImmediate(true);
        Spark.get("/demo", (req, res) -> {
            res.type("Application/JSON");
            ApplicationRequest appreq = buildGeneralRequest("DEMO", req.queryMap().toMap());
            LoggingUtils.logMessage("SPARK", "Request: %s", appreq.toString());
            ApplicationResult appres = ApplicationRunner.runApplication(appreq);
            if (appres.hasErrors()) {
                res.status(500);
                return appres.getErrors().stream()
                        .map(e1 -> String.format("    \"%s: %s\"", e1.getClass().getName(), e1.getMessage()))
                        .collect(Collectors.joining(",\n", "{\"errors\" : [\n", "]}"));
            }
            return converter.toJson(appres.getResult());
        });

        Spark.get("/generator", (req, res) -> {
            res.type("Application/JSON");
            ApplicationRequest appreq = buildGeneralRequest("DONUT", req.queryMap().toMap());
            LoggingUtils.logMessage("SPARK", "Request: %s", appreq.toString());
            ApplicationResult appres = ApplicationRunner.runApplication(appreq);
            if (appres.hasErrors()) {
                res.status(500);
                return appres.getErrors().stream()
                        .map(e1 -> String.format("    \"%s\"", e1.getMessage()))
                        .collect(Collectors.joining(",\n", "{\"errors\" : [\n", "]}"));
            }
            return converter.toJson(appres.getResult());
        });

        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
        });
    }

    private static ApplicationRequest buildGeneralRequest(String tag, Map<String, String[]> rawArgs) {
        Map<String, String> urlArgs = rawArgs.entrySet().stream()
                .peek(e -> {
                    if (e.getValue().length != 1) throw new IllegalArgumentException("Got weird params.");
                })
                .collect(StreamUtils.collectWithMapping(Map.Entry::getKey, e -> e.getValue()[0]));

        StartPoint st = new StartPoint(new double[] {
                Double.valueOf(urlArgs.get(LAT_TAG)),
                Double.valueOf(urlArgs.get(LONG_TAG))
        });

        String timeZone = TimezoneMapper.tzNameAt(st.getCoordinates()[0], st.getCoordinates()[1]);

        TimePoint startTime = urlArgs.containsKey(START_TIME_TAG)
                ? new TimePoint(1000L * Long.valueOf(urlArgs.get(START_TIME_TAG)), timeZone)
                : TimePoint.now(timeZone);

        TimeDelta maxDelta = new TimeDelta(1000L * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));

        String query = urlArgs.get(TYPE_TAG);

        return new ApplicationRequest.Builder(tag)
                .withStartPoint(st)
                .withStartTime(startTime)
                .withMaxDelta(maxDelta)
                .withQuery(new LocationType(query, query))
                .build();

    }
}
