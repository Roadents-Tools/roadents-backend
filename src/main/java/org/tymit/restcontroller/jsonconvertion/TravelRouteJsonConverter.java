package org.tymit.restcontroller.jsonconvertion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/16/16.
 */
public class TravelRouteJsonConverter implements JsonConverter<TravelRoute> {

    private static final String START_TAG = "start";
    private static final String START_TIME_TAG = "starttime";
    private static final String END_TAG = "dest";
    private static final String ROUTE_TAG = "route";
    private static final String STATION_LAT_TAG = "latitude";
    private static final String STATION_LONG_TAG = "longitude";
    private static final String STATION_NAME_TAG = "stationName";
    private static final String STATION_CHAIN_TAG = "trainBusName";

    private StartPointJsonConverter startConverter = new StartPointJsonConverter();
    private DestinationJsonConverter destConverter = new DestinationJsonConverter();

    @Override
    public String toJson(TravelRoute input) {
        JSONObject obj = new JSONObject();
        obj.put(START_TIME_TAG, input.getStartTime().getUnixTime()); //Store seconds from unix epoch
        obj.put(START_TAG, new JSONObject(startConverter.toJson(input.getStart())));
        obj.put(ROUTE_TAG, convertRoute(input));
        if (input.getDestination() != null)
            obj.put(END_TAG, new JSONObject(destConverter.toJson(input.getDestination())));
        return obj.toString();
    }

    private JSONArray convertRoute(TravelRoute input) {
        List<LocationPoint> route = input.getRoute();
        StartPoint start = input.getStart();
        DestinationLocation end = input.getDestination();

        JSONArray routeJson = new JSONArray();
        routeJson.put(new JSONObject(startConverter.toJson(start)));
        for (int i = 1; i < route.size() - 1; i++) {
            JSONObject stationObj = new JSONObject();

            TransStation station = (TransStation) route.get(i);
            String stationName = station.getName();
            stationObj.put(STATION_NAME_TAG, stationName);
            String chainName = station.getChain().getName();
            stationObj.put(STATION_CHAIN_TAG, chainName);
            double stationLat = station.getCoordinates()[0];
            stationObj.put(STATION_LAT_TAG, stationLat);
            double stationLong = station.getCoordinates()[1];
            stationObj.put(STATION_LONG_TAG, stationLong);

            routeJson.put(stationObj);
        }

        routeJson.put(new JSONObject(destConverter.toJson(end)));

        return routeJson;
    }

    @Override
    public TravelRoute fromJson(String json) {

        JSONObject jsonObject = new JSONObject(json);

        StartPoint start = startConverter.fromJson(jsonObject.getJSONObject(START_TAG).toString());
        TimeModel startTime = TimeModel.fromUnixTime(jsonObject.getLong(START_TIME_TAG));
        TravelRoute route = new TravelRoute(start, startTime);

        Map<String, TransChain> storedChains = new ConcurrentHashMap<>();
        JSONArray routeList = jsonObject.getJSONArray(ROUTE_TAG);
        for (int i = 1; i < routeList.length() - 1; i++) {

            JSONObject stationObj = routeList.getJSONObject(i);
            String stationName = stationObj.getString(STATION_NAME_TAG);
            double stationLat = stationObj.getDouble(STATION_LAT_TAG);
            double stationLong = stationObj.getDouble(STATION_LONG_TAG);
            String chainName = stationObj.getString(STATION_CHAIN_TAG);

            if (storedChains.get(chainName) == null) {
                TransChain chain = new TransChain(chainName);
                storedChains.put(chainName, chain);
            }
            TransStation station = new TransStation(stationName, new double[]{stationLat, stationLong}, null, storedChains.get(chainName));
            route.addStation(station);
        }

        DestinationLocation end = destConverter.fromJson(jsonObject.getJSONObject(END_TAG).toString());
        route.setDestination(end);
        return route;
    }
}
