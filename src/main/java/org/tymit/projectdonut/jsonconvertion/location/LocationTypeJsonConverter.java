package org.tymit.projectdonut.jsonconvertion.location;

import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.location.LocationType;

/**
 * Created by ilan on 7/16/17.
 */
public class LocationTypeJsonConverter implements JsonConverter<LocationType> {

    private static final String VISIBLE_NAME_TAG = "visible_name";
    private static final String ENCODED_NAME_TAG = "encoded_name";

    @Override
    public String toJson(LocationType input) {
        JSONObject obj = new JSONObject();
        obj.put(VISIBLE_NAME_TAG, input.getVisibleName());
        obj.put(ENCODED_NAME_TAG, input.getEncodedname());
        return obj.toString();
    }

    @Override
    public LocationType fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        return new LocationType(obj.getString(VISIBLE_NAME_TAG), obj.getString(ENCODED_NAME_TAG));
    }
}
