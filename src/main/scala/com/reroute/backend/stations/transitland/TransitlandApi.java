package com.reroute.backend.stations.transitland;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.utils.LoggingUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by ilan on 1/22/17.
 */
public class TransitlandApi {

    private static final String BASE_FEED_URL = "http://transit.land/api/v1/feeds";
    private static final String AREA_FORMAT = "bbox=%f,%f,%f,%f";

    private static final double MILES_TO_LAT = 1.0 / 70;
    private static final double MILES_TO_LONG = 1.0 / 70;


    private JSONObject callUrl(String url) {
        OkHttpClient client;
        client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();


        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response;
        JSONObject rawobj;
        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                return null;
            }
            rawobj = new JSONObject(response.body().string());
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return null;
        }

        return rawobj;
    }

    public List<URL> getFeedsInArea(LocationPoint center, Distance range, Map<String, String> restrict, Map<String, String> avoid) {
        String url = BASE_FEED_URL + "?per_page=500";
        if (center != null && range != null && range.inMeters() >= 0) {
            double latVal1 = center.getCoordinates()[0] - range.inMiles() * MILES_TO_LAT;
            double latVal2 = center.getCoordinates()[0] + range.inMiles() * MILES_TO_LAT;

            double lngVal1 = center.getCoordinates()[1] - range.inMiles() * MILES_TO_LONG;
            double lngVal2 = center.getCoordinates()[1] + range.inMiles() * MILES_TO_LONG;
            url += "&" + String.format(AREA_FORMAT, lngVal1, latVal1, lngVal2, latVal2);
        }

        try {
            JSONObject obj = callUrl(url);
            if (obj == null) return Collections.emptyList();

            JSONArray feeds = obj.getJSONArray("feeds");
            int len = feeds.length();
            List<URL> rval = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                JSONObject curobj = feeds.getJSONObject(i);
                System.out.println(curobj.toString(1));
                boolean works = (restrict == null || restrict.keySet()
                        .stream()
                        .allMatch(key -> curobj.get(key).equals(restrict.get(key))))
                        && (avoid == null || avoid.keySet()
                        .stream()
                        .noneMatch(key -> curobj.get(key).equals(avoid.get(key))));
                if (works) rval.add(new URL(curobj.getString("url")));
            }
            return rval;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyList();
        }
    }

}
