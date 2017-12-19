package com.reroute.displayers.lambdacontroller;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.common.collect.Lists;
import com.moodysalem.TimezoneMapper;
import com.reroute.backend.jsonconvertion.routing.TravelRouteJsonConverter;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.ApplicationRunner;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.displayers.testdisplay.listtestdisplayer.TestDisplayer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ilan on 6/4/17.
 */
public class LambdaHandler implements RequestStreamHandler {

    private static final String TAG = "DONUT";
    private static final String START_TIME_TAG = "starttime";
    private static final String LAT_TAG = "latitude";
    private static final String LONG_TAG = "longitude";
    private static final String TYPE_TAG = "type";
    private static final String TIME_DELTA_TAG = "timedelta";
    private static final String TEST_TAG = "test";
    private static final String HUMAN_READABLE_TAG = "display";

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {


        LoggingUtils.setPrintImmediate(true);
        Map<String, String> urlArgs = null;

        try {
            urlArgs = parseUrlArgs(input);
            if (!urlArgs.containsKey(LAT_TAG)
                    || !urlArgs.containsKey(LONG_TAG)
                    || !urlArgs.containsKey(TYPE_TAG)
                    || !urlArgs.containsKey(TIME_DELTA_TAG)) {
                throw new Exception();
            }
        } catch (Exception e) {
            LoggingUtils.logError(e);
            outputArgsError(urlArgs, output);
            return;
        }

        LoggingUtils.logMessage("DonutLambda", "/routes called with args %s", urlArgs.toString());
        long callTime = System.currentTimeMillis();

        String tag = urlArgs.getOrDefault("tag", TAG);
        ApplicationRequest.Builder builder = new ApplicationRequest.Builder(tag);
        try {

            StartPoint st = new StartPoint(new double[] {
                    Double.valueOf(urlArgs.get(LAT_TAG)),
                    Double.valueOf(urlArgs.get(LONG_TAG))
            });

            String timeZone = TimezoneMapper.tzNameAt(st.getCoordinates()[0], st.getCoordinates()[1]);

            TimePoint startTime = urlArgs.containsKey(START_TIME_TAG)
                    ? TimePoint.from(1000L * Long.valueOf(START_TIME_TAG), timeZone)
                    : TimePoint.now(timeZone);

            TimeDelta maxDelta = new TimeDelta(1000L * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));

            builder.withStartPoint(st)
                    .withStartTime(startTime)
                    .withMaxDelta(maxDelta);
        } catch (NumberFormatException e) {
            outputArgsError(urlArgs, output);
        }

        boolean test = Boolean.valueOf(urlArgs.get(TEST_TAG));
        builder.isTest(test);
        builder.withQuery(new LocationType(urlArgs.get(TYPE_TAG), urlArgs.get(TYPE_TAG)));
        boolean humanReadable = Boolean.valueOf(urlArgs.getOrDefault(HUMAN_READABLE_TAG, "false"));


        ApplicationResult results = null;
        try {
            results = ApplicationRunner.runApplication(builder.build());
        } catch (Exception e) {
            LoggingUtils.logError(e);
            outputInternalErrors(Stream.of(e), output);
        }

        if (results == null || (results.getResult().isEmpty() && results.getErrors().isEmpty())) {
            results = ApplicationResult.err(Lists.newArrayList(new Exception("Null results set.")));
        }
        if (results.hasErrors()) {
            results.getErrors().forEach(LoggingUtils::logError);
            outputInternalErrors(results.getErrors().stream(), output);
            return;
        }
        String rval;
        if (humanReadable) {
            List<TravelRoute> routes = results.getResult()
                    .parallelStream()
                    .collect(Collectors.toList());
            rval = TestDisplayer.buildDisplay(routes);
        } else {
            TravelRouteJsonConverter converter = new TravelRouteJsonConverter();
            JSONArray routesArray = results
                    .getResult()
                    .parallelStream()
                    .map(converter::toJson)
                    .collect(JSONArray::new, (r, s) -> r.put(new JSONObject(s)), (r, r2) -> {
                        for (int i = 0; i < r2.length(); i++) {
                            r.put(r2.getJSONObject(i));
                        }
                    });
            JSONObject rvalObj = new JSONObject();
            rvalObj.put("routes", routesArray);
            rval = rvalObj.toString();
        }
        long endTime = System.currentTimeMillis();
        long diffTime = endTime - callTime;
        LoggingUtils.logMessage(
                "DonutLambda",
                "Got %d routes in %f seconds.",
                results.getResult().size(),
                diffTime / 1000.0
        );

        outputData(rval, output);

    }

    private static Map<String, String> parseUrlArgs(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        Map<String, String> rval = new ConcurrentHashMap<>();
        StringBuilder inputStringBuilder = reader.lines()
                .collect(
                        StringBuilder::new,
                        StringBuilder::append,
                        StringBuilder::append
                );
        JSONObject event = new JSONObject(inputStringBuilder.toString());
        if (event.get("queryStringParameters") == null) throw new IllegalArgumentException("Got no parameters.");
        JSONObject qps = event.getJSONObject("queryStringParameters");
        for (Object keyObj : qps.keySet()) {
            String key = (String) keyObj;
            rval.put(key, qps.getString(key));
        }
        return rval;
    }

    private static void outputData(String rawOutputJson, OutputStream outputStream) throws IOException {
        JSONObject obj;
        try {
            obj = new JSONObject(rawOutputJson);
        } catch (JSONException e) {
            obj = null;
        }

        JSONObject responseJson = new JSONObject();
        JSONObject headerJson = new JSONObject();
        headerJson.put("content", "application/json");
        responseJson.put("headers", headerJson);
        responseJson.put("body", obj == null ? rawOutputJson : obj);
        responseJson.put("statusCode", "200");
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        writer.write(responseJson.toString());
        writer.close();
    }

    private static void outputArgsError(Map<String, String> urlArgs, OutputStream output) throws IOException {
        JSONObject headerJson = new JSONObject()
                .put("content", "application/json");
        JSONObject bodyJson = new JSONObject()
                .put("message", "Could not parse method arguments.")
                .put("arguments", urlArgs == null ? "Null" : urlArgs.toString());
        JSONObject responseJson = new JSONObject()
                .put("headers", headerJson)
                .put("body", bodyJson)
                .put("statusCode", "400");
        OutputStreamWriter writer = new OutputStreamWriter(output);
        writer.write(responseJson.toString());
        writer.close();
    }

    private static void outputInternalErrors(Stream<Exception> errors, OutputStream outputStream) throws IOException {
        JSONObject responseJson = new JSONObject();
        JSONObject headerJson = new JSONObject();
        headerJson.put("content", "application/json");
        responseJson.put("headers", headerJson);
        JSONArray exceptionArr = errors
                .map(ex -> new JSONObject()
                        .put("Type", ex.getClass().getName())
                        .put("Message", ex.getMessage())
                        .put("Stacktrace", Arrays.toString(ex.getStackTrace()))
                )
                .collect(JSONArray::new, JSONArray::put, (ar1, ar2) -> {
                    for (int i = 0, len = ar2.length(); i < len; i++) {
                        ar1.put(ar2.getJSONObject(i));
                    }
                });
        JSONObject bodyObj = new JSONObject()
                .put("message", "Internal exceptions occured.")
                .put("errors", exceptionArr);
        responseJson.put("body", bodyObj);
        responseJson.put("statusCode", "400");
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        writer.write(responseJson.toString());
        writer.close();
    }
}
