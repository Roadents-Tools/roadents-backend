package org.tymit.displayers.restcontroller;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tymit.displayers.testdisplay.listtestdisplayer.TestDisplayer;
import org.tymit.projectdonut.jsonconvertion.location.DestinationJsonConverter;
import org.tymit.projectdonut.jsonconvertion.routing.TravelRouteJsonConverter;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/15/16.
 */

@RestController
public class DonutController {

    private static final String TAG = "DONUT";
    private static final String START_TIME_TAG = "starttime";
    private static final String LAT_TAG = "latitude";
    private static final String LONG_TAG = "longitude";
    private static final String TYPE_TAG = "type";
    private static final String TIME_DELTA_TAG = "timedelta";
    private static final String TEST_TAG = "test";
    private static final String HUMAN_READABLE_TAG = "display";

    @RequestMapping("/routes")
    public String getRoutes(@RequestParam Map<String, String> urlArgs) {

        LoggingUtils.setPrintImmediate(true);
        LoggingUtils.logMessage("DonutController", "/routes called with args %s", urlArgs.toString());
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
        } catch (Exception e){
            LoggingUtils.logError(e);
        }

        if (results == null) {
            results = new HashMap<>();
            results.put("ERRORS", new ArrayList<>());
            results.get("ERRORS").add(new Exception("Null results set."));
        }
        if (results.containsKey("ERRORS")){
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
        LoggingUtils.logMessage("DonutController", "Got %d routes in %f seconds.", results.get("ROUTES")
                .size(), diffTime / 1000.0);
        return rval;

    }

    @RequestMapping("/destinations")
    public String getDests(@RequestParam Map<String, String> urlArgs) {

        LoggingUtils.setPrintImmediate(true);
        LoggingUtils.logMessage("DonutController", "/destinations called with args %s", urlArgs.toString());
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
        DestinationJsonConverter converter = new DestinationJsonConverter();
        JSONArray dests = ApplicationRunner.runApplication(tag, args)
                .get("DESTS")
                .parallelStream()
                .map(destObj -> (DestinationLocation) destObj)
                .map(converter::toJson)
                .map(JSONObject::new)
                .collect(JSONArray::new, JSONArray::put, (r, r2) -> {
                    for (int i = 0, r2len = r2.length(); i < r2len; i++) {
                        r.put(r2.getJSONObject(i));
                    }
                });


        long endTime = System.currentTimeMillis();
        long diffTime = endTime - callTime;
        LoggingUtils.logMessage("DonutController", "Got %d dest in %f seconds.", dests.length(), diffTime / 1000.0);
        return dests.toString();
    }


}
