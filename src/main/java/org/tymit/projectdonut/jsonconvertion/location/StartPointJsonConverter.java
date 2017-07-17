package org.tymit.projectdonut.jsonconvertion.location;

import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.location.StartPoint;

/**
 * Created by ilan on 7/16/16.
 */
public class StartPointJsonConverter implements JsonConverter<StartPoint> {

    private static final String LATITUDE_TAG = "latitude";
    private static final String LONGITUDE_TAG = "longitude";

    @Override
    public String toJson(StartPoint input) {
        JSONObject obj = new JSONObject();
        obj.put(LATITUDE_TAG, input.getCoordinates()[0]);
        obj.put(LONGITUDE_TAG, input.getCoordinates()[1]);
        return obj.toString();
    }

    @Override
    public StartPoint fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        double lat = obj.getDouble(LATITUDE_TAG);
        double lonj = obj.getDouble(LONGITUDE_TAG);
        return new StartPoint(new double[]{lat, lonj});
    }
}
