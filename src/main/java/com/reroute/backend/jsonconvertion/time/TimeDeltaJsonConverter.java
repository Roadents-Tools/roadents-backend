package com.reroute.backend.jsonconvertion.time;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.model.time.TimeDelta;
import org.json.JSONObject;

/**
 * Created by ilan on 6/3/17.
 */
public class TimeDeltaJsonConverter implements JsonConverter<TimeDelta> {

    private static final String TIME_DELTA_TAG = "delta";

    @Override
    public String toJson(TimeDelta input) {
        return String.format("{\"%s\" : %d}", TIME_DELTA_TAG, input.getDeltaLong());
    }

    @Override
    public JSONObject toJsonObject(TimeDelta input) {
        return new JSONObject().put(TIME_DELTA_TAG, input.getDeltaLong());
    }

    @Override
    public TimeDelta fromJsonObject(JSONObject obj) {
        return new TimeDelta(obj.getLong(TIME_DELTA_TAG));
    }
}
