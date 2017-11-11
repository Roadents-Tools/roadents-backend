package com.reroute.backend.jsonconvertion.location;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.model.location.LocationType;
import org.json.JSONObject;

/**
 * Created by ilan on 7/16/17.
 */
public class LocationTypeJsonConverter implements JsonConverter<LocationType> {

    private static final String VISIBLE_NAME_TAG = "visible_name";
    private static final String ENCODED_NAME_TAG = "encoded_name";

    @Override
    public JSONObject toJsonObject(LocationType input) {
        JSONObject obj = new JSONObject();
        obj.put(VISIBLE_NAME_TAG, input.getVisibleName());
        obj.put(ENCODED_NAME_TAG, input.getEncodedname());
        return obj;
    }

    @Override
    public LocationType fromJsonObject(JSONObject obj) {
        return new LocationType(obj.getString(VISIBLE_NAME_TAG), obj.getString(ENCODED_NAME_TAG));
    }
}
