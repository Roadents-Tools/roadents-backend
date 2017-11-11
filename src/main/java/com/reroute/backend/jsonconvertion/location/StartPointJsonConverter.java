package com.reroute.backend.jsonconvertion.location;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.model.location.StartPoint;
import org.json.JSONObject;

/**
 * Created by ilan on 7/16/16.
 */
public class StartPointJsonConverter implements JsonConverter<StartPoint> {

    private static final String LATITUDE_TAG = "latitude";
    private static final String LONGITUDE_TAG = "longitude";

    @Override
    public JSONObject toJsonObject(StartPoint input) {
        JSONObject obj = new JSONObject();
        obj.put(LATITUDE_TAG, input.getCoordinates()[0]);
        obj.put(LONGITUDE_TAG, input.getCoordinates()[1]);
        return obj;
    }

    @Override
    public StartPoint fromJsonObject(JSONObject obj) {
        double lat = obj.getDouble(LATITUDE_TAG);
        double lonj = obj.getDouble(LONGITUDE_TAG);
        return new StartPoint(new double[]{lat, lonj});
    }
}
