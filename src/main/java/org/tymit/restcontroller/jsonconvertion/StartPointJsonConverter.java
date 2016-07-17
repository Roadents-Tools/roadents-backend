package org.tymit.restcontroller.jsonconvertion;

import org.json.JSONObject;
import org.tymit.projectdonut.model.StartPoint;

/**
 * Created by ilan on 7/16/16.
 */
public class StartPointJsonConverter implements JsonConverter<StartPoint> {

    private static final String COORDS_TAG = "startpoint";

    @Override
    public String toJson(StartPoint input) {
        JSONObject obj = new JSONObject();
        obj.put(COORDS_TAG, input.getCoordinates());
        return obj.toString();
    }

    @Override
    public StartPoint fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        double lat = obj.getJSONArray(COORDS_TAG).getDouble(0);
        double lonj = obj.getJSONArray(COORDS_TAG).getDouble(1);
        return new StartPoint(new double[]{lat, lonj});
    }
}
