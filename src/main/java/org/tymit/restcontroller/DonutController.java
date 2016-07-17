package org.tymit.restcontroller;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tymit.projectdonut.logic.ApplicationRunner;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.TravelRoute;
import org.tymit.projectdonut.utils.LoggingUtils;
import org.tymit.restcontroller.jsonconvertion.DestinationJsonConverter;
import org.tymit.restcontroller.jsonconvertion.TravelRouteJsonConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ilan on 7/15/16.
 */

@RestController
public class DonutController {

    private static String TAG = "DONUT";
    private static String START_TIME_TAG = "starttime";
    private static String LAT_TAG = "latitude";
    private static String LONG_TAG = "longitude";
    private static String TYPE_TAG = "type";
    private static String TIME_DELTA_TAG = "timedelta";
    private static String TEST_TAG = "test";

    @RequestMapping("/routes")
    public String getRoutes(@RequestParam Map<String, String> urlArgs) {
        LoggingUtils.setPrintImmediate(true);
        LoggingUtils.logMessage("DonutController", "/routes called with args %s", urlArgs.toString());

        String tag = urlArgs.getOrDefault("tag", TAG);
        Map<String, Object> args = new HashMap<>();

        long startTime = (urlArgs.containsKey(START_TIME_TAG))
                ? 1000l * Long.valueOf(urlArgs.get(START_TIME_TAG))
                : System.currentTimeMillis();
        args.put(START_TIME_TAG, startTime);

        args.put(LAT_TAG, Double.valueOf(urlArgs.get(LAT_TAG)));
        args.put(LONG_TAG, Double.valueOf(urlArgs.get(LONG_TAG)));
        args.put(TIME_DELTA_TAG, 1000l * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));
        args.put(TYPE_TAG, urlArgs.get(TYPE_TAG));
        boolean test = Boolean.valueOf(urlArgs.get(TEST_TAG));
        args.put(TEST_TAG, test);
        TravelRouteJsonConverter converter = new TravelRouteJsonConverter();
        JSONArray routes = ApplicationRunner.runApplication(tag, args)
                .get("ROUTES")
                .parallelStream()
                .map(routeObj -> (TravelRoute) routeObj)
                .map(route -> converter.toJson(route))
                .collect(() -> new JSONArray(), (r, s) -> r.put(new JSONObject(s)), (r, r2) -> {
                    for (int i = 0; i < r2.length(); i++) {
                        r.put(r2.getJSONObject(i));
                    }
                });
        LoggingUtils.logMessage("DonutController", "Got %d dests", routes.length());
        return routes.toString();

    }

    @RequestMapping("/destinations")
    public String getDests(@RequestParam Map<String, String> urlArgs) {
        LoggingUtils.setPrintImmediate(true);

        LoggingUtils.logMessage("DonutController", "/destinations called with args %s", urlArgs.toString());


        String tag = urlArgs.getOrDefault("tag", TAG);
        Map<String, Object> args = new HashMap<>();

        long startTime = (urlArgs.containsKey(START_TIME_TAG))
                ? 1000l * Long.valueOf(urlArgs.get(START_TIME_TAG))
                : System.currentTimeMillis();
        args.put(START_TIME_TAG, startTime);

        args.put(LAT_TAG, Double.valueOf(urlArgs.get(LAT_TAG)));
        args.put(LONG_TAG, Double.valueOf(urlArgs.get(LONG_TAG)));
        args.put(TIME_DELTA_TAG, 1000l * Long.valueOf(urlArgs.get(TIME_DELTA_TAG)));
        args.put(TYPE_TAG, urlArgs.get(TYPE_TAG));
        boolean test = Boolean.valueOf(urlArgs.get(TEST_TAG));
        args.put(TEST_TAG, test);
        DestinationJsonConverter converter = new DestinationJsonConverter();
        JSONArray dests = ApplicationRunner.runApplication(tag, args)
                .get("DESTS")
                .parallelStream()
                .map(destObj -> (DestinationLocation) destObj)
                .map(dest -> new JSONObject(converter.toJson(dest)))
                .collect(() -> new JSONArray(), (r, s) -> r.put(s), (r, r2) -> {
                    for (int i = 0; i < r2.length(); i++) {
                        r.put(r2.getJSONObject(i));
                    }
                });

        LoggingUtils.logMessage("DonutController", "Got %d routes", dests.length());
        return dests.toString();
    }


}
