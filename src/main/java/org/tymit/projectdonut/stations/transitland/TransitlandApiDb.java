package org.tymit.projectdonut.stations.transitland;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.helpers.StationChainCacheHelper;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ilan on 1/22/17.
 */
public class TransitlandApiDb implements StationDbInstance.ComboDb {

    private static final int MAX_QUERY_SIZE = 100;

    private static final String BASE_SCHEDULE_URL = "http://transit.land/api/v1/schedule_stop_pairs";
    private static final String BASE_FEED_URL = "http://transit.land/api/v1/feeds";
    private static final String AREA_FORMAT = "bbox=%f,%f,%f,%f";
    private static final String ROUTE_FORMAT = "route_onestop_id=%s";
    private static final String TRIP_FORMAT = "trip=%s";
    private static final String SCHEDULE_FORMAT = "origin_departure_between=%d:%d,%d:%d&date=%d-%d-%d";
    private static final String STOP_ID_URL = "http://transit.land/api/v1/stops?per_page=" + MAX_QUERY_SIZE + "&onestop_id=%s";

    private static final double MILES_TO_LAT = 1.0 / 69.5;
    private static final double MILES_TO_LONG = 1 / 69.5;

    private boolean isUp;


    public TransitlandApiDb() {
        isUp = true;
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain) {

        //Get scheduling info
        String scheduleUrl = buildScheduleUrl(center, range, start, maxDelta, chain);
        JSONObject rawobj = callUrl(scheduleUrl);

        if (rawobj == null) {
            //if (isUp) seedStations(center, range);
            return Collections.emptyList();
        }

        JSONArray schedStopArr = rawobj.getJSONArray("schedule_stop_pairs");
        int size = schedStopArr.length();
        if (size <= 0) return Collections.emptyList();

        //Get stop information
        AtomicInteger numOfStops = new AtomicInteger(0);
        StringJoiner ids = IntStream.range(0, size)
                .parallel().boxed()
                .map(schedStopArr::getJSONObject)
                .map(obj -> obj.getString("origin_onestop_id"))
                .distinct()
                .peek(obj -> numOfStops.getAndIncrement())
                .collect(() -> new StringJoiner(","), StringJoiner::add, StringJoiner::merge);
        String stopQuery = String.format(STOP_ID_URL, ids.toString());
        JSONObject rawStopObj = callUrl(stopQuery);
        if (rawStopObj == null) return Collections.emptyList();

        Map<String, String[]> idToStopInfo = new ConcurrentHashMap<>(size);
        JSONArray stopInfoArray = rawStopObj.getJSONArray("stops");
        IntStream.range(0, numOfStops.get())
                .boxed().parallel()
                .map(stopInfoArray::getJSONObject)
                .forEach(obj -> {
                    String id = obj.getString("onestop_id");
                    String name = obj.getString("name");
                    JSONArray coords = obj.getJSONObject("geometry")
                            .getJSONArray("coordinates");
                    idToStopInfo.put(id, new String[] { name, "" + coords.getDouble(1), "" + coords
                            .getDouble(0) });
                });

        //Match & return
        Map<String, TransChain> chainMap = new ConcurrentHashMap<>();
        if (chain != null) chainMap.put(chain.getName(), chain);
        List<TransStation> rval = IntStream.range(0, size)
                .boxed().parallel()
                .map(schedStopArr::getJSONObject)
                .map(obj -> mapToStation(obj, chainMap, idToStopInfo))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        return rval;
    }

    private static String buildScheduleUrl(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain) {
        StringBuilder builder = new StringBuilder("?per_page=").append(MAX_QUERY_SIZE)
                .append("&");
        if (center != null && range >= 0) {

            //Construct the bounding box
            double latVal1 = center[0] - range * MILES_TO_LAT;
            double latVal2 = center[0] + range * MILES_TO_LAT;

            double lngVal1 = center[1] - range * MILES_TO_LONG;
            double lngVal2 = center[1] + range * MILES_TO_LONG;

            builder.append(String.format(AREA_FORMAT, lngVal1, latVal1, lngVal2, latVal2));
            builder.append("&");
        }
        if (chain != null && !chain.getName().equals("")) {
            String[] routeTrip = chain.getName().split(" TripID: ");
            if (routeTrip.length <= 1)
                builder.append(String.format(TRIP_FORMAT, chain.getName()));
            else builder.append(String.format(ROUTE_FORMAT, routeTrip[0]))
                    .append("&")
                    .append(String.format(TRIP_FORMAT, routeTrip[1]))
                    .append("&");
        }
        if (start != null && maxDelta != null && maxDelta != TimeDelta.NULL) {
            TimePoint maxTime = start.plus(maxDelta);
            builder.append(String.format(SCHEDULE_FORMAT, start.getHour(), start
                            .getMinute(),
                    (maxTime.getHour() > start.getHour()) ? maxTime.getHour() : maxTime
                            .getHour() + 24, maxTime.getMinute(),
                    start.getYear(), start.getMonth(), start.getDayOfMonth())
            )
                    .append("&");

        }
        return BASE_SCHEDULE_URL + builder.toString();
    }

    private TransStation mapToStation(JSONObject obj, Map<String, TransChain> chains, Map<String, String[]> stopInfo) {
        String stationId = obj.getString("origin_onestop_id");
        String[] stationInfo = stopInfo.get(stationId);
        String name = stationInfo[0];
        double[] coords = new double[] { Double.valueOf(stationInfo[1]), Double.valueOf(stationInfo[2]) };

        String chainName = obj.getString("route_onestop_id") + " TripID: " + obj
                .getString("trip");
        chains.putIfAbsent(chainName, new TransChain(chainName));
        TransChain chain = chains.get(chainName);

        String[] timeSchedule = obj.getString("origin_departure_time")
                .split(":");

        SchedulePoint departTime = new SchedulePoint(
                Integer.valueOf(timeSchedule[0]) % 24,
                Integer.valueOf(timeSchedule[1]),
                ((timeSchedule.length > 2) ? Integer.valueOf(timeSchedule[2]) : 0),
                null,
                60
        );

        return new TransStation(name, coords, Collections.singletonList(departTime), chain);
    }

    private JSONObject callUrl(String url) {
        OkHttpClient client;
        client = new OkHttpClient();


        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response;
        JSONObject rawobj;
        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                isUp = false;
                return null;
            }
            rawobj = new JSONObject(response.body().string());
        } catch (SocketTimeoutException e) {
            LoggingUtils.logError(e);
            return null;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isUp = false;
            return null;
        }

        return rawobj;
    }

    private CompletableFuture<Boolean> seedStations(double[] center, double range) {
        return CompletableFuture.supplyAsync(() -> {
            Stream<List<TransStation>> srcStream = getFeedsInArea(center, range, null, null)
                    .parallelStream()
                    .map(url -> new GtfsProvider(url)
                            .getUpdatedStations()
                            .values()
                            .stream()
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList())
                    );
            StationChainCacheHelper h = StationChainCacheHelper.getHelper();
            return h.cacheStations(center, range, null, null, srcStream);
        });
    }

    public List<URL> getFeedsInArea(double[] center, double range, Map<String, String> restrict, Map<String, String> avoid) {
        String url = BASE_FEED_URL + "?per_page=500";
        if (center != null && range >= 0) {
            double latVal1 = center[0] - range * MILES_TO_LAT;
            double latVal2 = center[0] + range * MILES_TO_LAT;

            double lngVal1 = center[1] - range * MILES_TO_LONG;
            double lngVal2 = center[1] + range * MILES_TO_LONG;
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
                else System.out.printf("%s got filtered.\n", curobj.toString());
            }
            return rval;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyList();
        }
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        return true;
    }

    @Override
    public boolean isUp() {
        return isUp;
    }

    @Override
    public void close() {
    }
}
