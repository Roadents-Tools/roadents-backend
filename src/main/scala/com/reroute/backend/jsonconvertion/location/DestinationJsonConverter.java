package com.reroute.backend.jsonconvertion.location;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationType;
import org.json.JSONObject;

/**
 * Created by ilan on 7/16/16.
 */
public class DestinationJsonConverter implements JsonConverter<DestinationLocation> {

    private static final String NAME_TAG = "name";
    private static final String TYPE_TAG = "type";
    private static final String ADDRESS_TAG = "address";
    private static final String LATITTUDE_TAG = "latitude";
    private static final String LONGITUDE_TAG = "longitude";

    @Override
    public JSONObject toJsonObject(DestinationLocation input) {
        JSONObject obj = new JSONObject();
        JSONObject typeObj = new JSONObject(new LocationTypeJsonConverter().toJson(input.getType()));
        obj.put(NAME_TAG, input.getName());
        obj.put(TYPE_TAG, typeObj);
        obj.put(LATITTUDE_TAG, input.getCoordinates()[0]);
        obj.put(LONGITUDE_TAG, input.getCoordinates()[1]);
        input.getAddress().ifPresent(addr -> obj.put(ADDRESS_TAG, addr));
        return obj;
    }

    @Override
    public DestinationLocation fromJsonObject(JSONObject obj) {
        String name = obj.getString(NAME_TAG);
        double latitude = obj.getDouble(LATITTUDE_TAG);
        double longitude = obj.getDouble(LONGITUDE_TAG);
        JSONObject typeObj = obj.getJSONObject(TYPE_TAG);
        LocationType type = new LocationTypeJsonConverter().fromJson(typeObj.toString());
        String address = null;
        if (obj.has(ADDRESS_TAG)) {
            address = obj.getString(ADDRESS_TAG);
            if (address.toLowerCase().equals("null") || address.length() < 2) {
                address = null;
            }
        }
        return new DestinationLocation(name, type, new double[] { latitude, longitude }, address);
    }
}
