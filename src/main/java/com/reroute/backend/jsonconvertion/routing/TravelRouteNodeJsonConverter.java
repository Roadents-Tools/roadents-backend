package com.reroute.backend.jsonconvertion.routing;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.jsonconvertion.location.DestinationJsonConverter;
import com.reroute.backend.jsonconvertion.location.StartPointJsonConverter;
import com.reroute.backend.jsonconvertion.location.TransStationJsonConverter;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.utils.LoggingUtils;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 8/22/16.
 */
public class TravelRouteNodeJsonConverter implements JsonConverter<TravelRouteNode> {

    private static final String WALK_TIME_TAG = "walkTimeFromPrev";
    private static final String WAIT_TIME_TAG = "waitTimeFromPrev";
    private static final String TRAVEL_TIME_TAG = "travelTimeFromPrev";
    private static final String LOCATION_TAG = "pt";

    private static final String START_POINT_TAG = "startpt";
    private static final String DEST_POINT_TAG = "destpt";
    private static final String STATION_POINT_TAG = "stationpt";

    private static final String STATION_LAT_TAG = "latitude";
    private static final String STATION_LONG_TAG = "longitude";
    private static final String STATION_NAME_TAG = "stationName";
    private static final String STATION_CHAIN_TAG = "trainBusName";

    private static final JSONObject ERROR = new JSONObject().put("ERROR", "Unknown error occured.");


    private final Map<String, TransChain> chainBucket;
    private StartPointJsonConverter startConv;
    private DestinationJsonConverter destConv;
    private TransStationJsonConverter statConv;

    public TravelRouteNodeJsonConverter() {
        chainBucket = new ConcurrentHashMap<>();
    }

    public TravelRouteNodeJsonConverter(Map<String, TransChain> sharedBucket) {
        chainBucket = sharedBucket;
    }

    @Override
    public String toJson(TravelRouteNode input) {
        JSONObject obj = new JSONObject();
        obj.put(WAIT_TIME_TAG, input.getWaitTimeFromPrev().getDeltaLong());
        obj.put(TRAVEL_TIME_TAG, input.getTravelTimeFromPrev().getDeltaLong());
        obj.put(WALK_TIME_TAG, input.getWalkTimeFromPrev().getDeltaLong());
        obj.put(LOCATION_TAG, extractPt(input));
        return obj.toString();
    }


    private JSONObject extractPt(TravelRouteNode input) {
        JSONObject obj = new JSONObject();
        LocationPoint pt = input.getPt();

        if (pt instanceof StartPoint) {
            StartPoint startPoint = (StartPoint) pt;
            if (startConv == null) startConv = new StartPointJsonConverter();
            JSONObject ptData = new JSONObject(startConv.toJson(startPoint));
            obj.put(START_POINT_TAG, ptData);
            return obj;
        } else if (pt instanceof DestinationLocation) {
            DestinationLocation dest = (DestinationLocation) pt;
            if (destConv == null) destConv = new DestinationJsonConverter();
            JSONObject ptData = new JSONObject(destConv.toJson(dest));
            obj.put(DEST_POINT_TAG, ptData);
            return obj;
        } else if (pt instanceof TransStation) {
            if (statConv == null) statConv = new TransStationJsonConverter();
            obj.put(STATION_POINT_TAG, new JSONObject(statConv.toJson((TransStation) pt)));
            return obj;
        } else {
            LoggingUtils.logError(getClass().getName() + "::extractPt", "pt was not instance of known types.");
            return ERROR;
        }
    }

    @Override
    public TravelRouteNode fromJson(String json) {

        JSONObject obj = new JSONObject(json);

        return new TravelRouteNode.Builder()
                .setTravelTime(obj.getLong(TRAVEL_TIME_TAG))
                .setWaitTime(obj.getLong(WAIT_TIME_TAG))
                .setWalkTime(obj.getLong(WALK_TIME_TAG))
                .setPoint(extractPt(obj.getJSONObject(LOCATION_TAG)))
                .build();
    }

    private LocationPoint extractPt(JSONObject input) {

        if (input.has(START_POINT_TAG)) {
            if (startConv == null) startConv = new StartPointJsonConverter();
            return startConv.fromJson(input.getJSONObject(START_POINT_TAG).toString());
        } else if (input.has(DEST_POINT_TAG)) {
            if (destConv == null) destConv = new DestinationJsonConverter();
            return destConv.fromJson(input.getJSONObject(DEST_POINT_TAG).toString());
        } else if (input.has(STATION_POINT_TAG)) {
            if (statConv == null) statConv = new TransStationJsonConverter();
            return statConv.fromJson(input.getJSONObject(STATION_POINT_TAG).toString(), chainBucket);
        } else {
            LoggingUtils.logError(getClass().getName() + "::extractPt", "pt was not instance of known types.");
            return null;
        }
    }

}
