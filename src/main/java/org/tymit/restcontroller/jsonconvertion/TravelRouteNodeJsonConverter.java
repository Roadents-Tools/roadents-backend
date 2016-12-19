package org.tymit.restcontroller.jsonconvertion;

import org.json.JSONObject;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.model.TravelRouteNode;
import org.tymit.projectdonut.utils.LoggingUtils;

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

    public TravelRouteNodeJsonConverter() {
        chainBucket = new ConcurrentHashMap<>();
    }

    public TravelRouteNodeJsonConverter(Map<String, TransChain> sharedBucket) {
        chainBucket = sharedBucket;
    }

    @Override
    public String toJson(TravelRouteNode input) {
        JSONObject obj = new JSONObject();
        obj.put(WAIT_TIME_TAG, input.getWaitTimeFromPrev());
        obj.put(TRAVEL_TIME_TAG, input.getTravelTimeFromPrev());
        obj.put(WALK_TIME_TAG, input.getWalkTimeFromPrev());
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
            obj.put(STATION_POINT_TAG, convertStation((TransStation) pt));
            return obj;
        } else {
            LoggingUtils.logError(getClass().getName() + "::extractPt", "pt was not instance of known types.");
            return ERROR;
        }
    }

    private JSONObject convertStation(TransStation station) {
        JSONObject stationObj = new JSONObject();

        String stationName = station.getName();
        stationObj.put(STATION_NAME_TAG, stationName);
        String chainName = station.getChain().getName();
        stationObj.put(STATION_CHAIN_TAG, chainName);
        double stationLat = station.getCoordinates()[0];
        stationObj.put(STATION_LAT_TAG, stationLat);
        double stationLong = station.getCoordinates()[1];
        stationObj.put(STATION_LONG_TAG, stationLong);

        return stationObj;
    }

    @Override
    public TravelRouteNode fromJson(String json) {

        JSONObject obj = new JSONObject(json);

        return new TravelRouteNode.Builder().setTravelTime(obj.getLong(TRAVEL_TIME_TAG))
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
            return convertStation(input.getJSONObject(STATION_POINT_TAG));
        } else {
            LoggingUtils.logError(getClass().getName() + "::extractPt", "pt was not instance of known types.");
            return null;
        }
    }

    private TransStation convertStation(JSONObject jsonObj) {
        String stationName = jsonObj.getString(STATION_NAME_TAG);
        double stationLat = jsonObj.getDouble(STATION_LAT_TAG);
        double stationLong = jsonObj.getDouble(STATION_LONG_TAG);
        String chainName = jsonObj.getString(STATION_CHAIN_TAG);

        if (chainBucket.get(chainName) == null) {
            TransChain chain = new TransChain(chainName);
            chainBucket.put(chainName, chain);
        }
        return new TransStation(stationName, new double[] { stationLat, stationLong }, null, chainBucket.get(chainName));
    }

}
