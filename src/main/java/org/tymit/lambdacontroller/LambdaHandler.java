package org.tymit.lambdacontroller;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.routing.TravelRouteJsonConverter;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.utils.LoggingUtils;
import org.tymit.restcontroller.testdisplay.TestDisplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

        Map<String, String> urlArgs = parseUrlArgs(input);

        LoggingUtils.logMessage("DonutLambda", "/routes called with args %s", urlArgs.toString());
        long callTime = System.currentTimeMillis();

        String tag = urlArgs.getOrDefault("tag", TAG);
        Map<String, Object> args = new HashMap<>();

        long startTime = (urlArgs.containsKey(START_TIME_TAG))
                ? 1000L * Long.valueOf(urlArgs.get(START_TIME_TAG))
                : System.currentTimeMillis();
        args.put(START_TIME_TAG, startTime);

        args.put(LAT_TAG, Double.valueOf(urlArgs.get(LAT_TAG)));
        args.put(LONG_TAG, Double.valueOf(urlArgs.get(LONG_TAG)));
        args.put(TIME_DELTA_TAG, 1000L * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));
        args.put(TYPE_TAG, urlArgs.get(TYPE_TAG));
        boolean test = Boolean.valueOf(urlArgs.get(TEST_TAG));
        args.put(TEST_TAG, test);
        boolean humanReadable = Boolean.valueOf(urlArgs.getOrDefault(HUMAN_READABLE_TAG, "false"));
        TravelRouteJsonConverter converter = new TravelRouteJsonConverter();

        Map<String, List<Object>> results = null;
        try {
            results = ApplicationRunner.runApplication(tag, args);
        } catch (Exception e) {
            LoggingUtils.logError(e);
        }

        if (results == null) {
            results = new HashMap<>();
            results.put("ERRORS", new ArrayList<>());
            results.get("ERRORS").add(new Exception("Null results set."));
        }
        if (results.containsKey("ERRORS")) {
            results.get("ERRORS").forEach(errObj -> LoggingUtils.logError((Exception) errObj));
        }
        String rval;
        if (humanReadable) {
            List<TravelRoute> routes = results.get("ROUTES")
                    .parallelStream()
                    .map(routeObj -> (TravelRoute) routeObj)
                    .collect(Collectors.toList());
            rval = TestDisplayer.buildDisplay(routes);
        } else {
            rval = results
                    .get("ROUTES")
                    .parallelStream()
                    .map(routeObj -> (TravelRoute) routeObj)
                    .map(converter::toJson)
                    .collect(JSONArray::new, (r, s) -> r.put(new JSONObject(s)), (r, r2) -> {
                        for (int i = 0; i < r2.length(); i++) {
                            r.put(r2.getJSONObject(i));
                        }
                    })
                    .toString();
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
        JSONObject qps = (JSONObject) event.get("queryStringParameters");
        for (Object keyObj : qps.keySet()) {
            String key = (String) keyObj;
            rval.put(key, qps.getString(key));
        }
        return rval;
    }

    private static void outputData(String rawOutputJson, OutputStream outputStream) throws IOException {
        JSONObject responseJson = new JSONObject();
        JSONObject headerJson = new JSONObject();
        headerJson.put("content", "application/html");
        responseJson.put("headers", headerJson);
        responseJson.put("body", rawOutputJson);
        responseJson.put("statusCode", "200");
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        writer.write(responseJson.toString());
        writer.close();
    }
}
