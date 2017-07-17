package org.tymit.projectdonut.jsonconvertion.location;

import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

/**
 * Created by ilan on 7/16/16.
 */
public class DestinationJsonConverter implements JsonConverter<DestinationLocation> {

    private static final String NAME_TAG = "name";
    private static final String TYPE_TAG = "type";
    private static final String LATITTUDE_TAG = "latitude";
    private static final String LONGITUDE_TAG = "longitude";

    @Override
    public String toJson(DestinationLocation input) {
        JSONObject obj = new JSONObject();
        JSONObject typeObj = new JSONObject(new LocationTypeJsonConverter().toJson(input.getType()));
        obj.put(NAME_TAG, input.getName());
        obj.put(TYPE_TAG, typeObj);
        obj.put(LATITTUDE_TAG, input.getCoordinates()[0]);
        obj.put(LONGITUDE_TAG, input.getCoordinates()[1]);
        return obj.toString();
    }

    @Override
    public DestinationLocation fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        String name = obj.getString(NAME_TAG);
        double latitude = obj.getDouble(LATITTUDE_TAG);
        double longitude = obj.getDouble(LONGITUDE_TAG);
        JSONObject typeObj = obj.getJSONObject(TYPE_TAG);
        LocationType type = new LocationTypeJsonConverter().fromJson(typeObj.toString());
        return new DestinationLocation(name, type, new double[] { latitude, longitude });
    }
}
