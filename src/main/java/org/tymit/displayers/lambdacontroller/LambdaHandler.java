package org.tymit.displayers.lambdacontroller;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tymit.displayers.testdisplay.listtestdisplayer.TestDisplayer;
import org.tymit.projectdonut.jsonconvertion.routing.TravelRouteJsonConverter;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        Map<String, Object> args = new HashMap<>();

        try {

            long startTime = (urlArgs.containsKey(START_TIME_TAG))
                    ? 1000L * Long.valueOf(urlArgs.get(START_TIME_TAG))
                    : System.currentTimeMillis();
            args.put(START_TIME_TAG, startTime);
            args.put(LAT_TAG, Double.valueOf(urlArgs.get(LAT_TAG)));
            args.put(LONG_TAG, Double.valueOf(urlArgs.get(LONG_TAG)));
            args.put(TIME_DELTA_TAG, 1000L * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));
        } catch (NumberFormatException e) {
            outputArgsError(urlArgs, output);
        }

        boolean test = Boolean.valueOf(urlArgs.get(TEST_TAG));
        args.put(TEST_TAG, test);
        args.put(TYPE_TAG, urlArgs.get(TYPE_TAG));
        boolean humanReadable = Boolean.valueOf(urlArgs.getOrDefault(HUMAN_READABLE_TAG, "false"));


        Map<String, List<Object>> results = null;
        try {
            results = ApplicationRunner.runApplication(tag, args);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            outputInternalErrors(Stream.of(e), output);
        }

        if (results == null || !results.containsKey("ROUTES")) {
            results = new HashMap<>();
            results.put("ERRORS", new ArrayList<>());
            results.get("ERRORS").add(new Exception("Null results set."));
        }
        if (results.containsKey("ERRORS")) {
            results.get("ERRORS").forEach(errObj -> LoggingUtils.logError((Exception) errObj));
            outputInternalErrors(results.get("ERRORS").stream().map(obj -> (Exception) obj), output);
            return;
        }
        String rval;
        if (humanReadable) {
            List<TravelRoute> routes = results.get("ROUTES")
                    .parallelStream()
                    .map(routeObj -> (TravelRoute) routeObj)
                    .collect(Collectors.toList());
            rval = TestDisplayer.buildDisplay(routes);
        } else {
            TravelRouteJsonConverter converter = new TravelRouteJsonConverter();
            JSONArray routesArray = results
                    .get("ROUTES")
                    .parallelStream()
                    .map(routeObj -> (TravelRoute) routeObj)
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
                results.get("ROUTES").size(),
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
