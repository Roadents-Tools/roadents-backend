package org.tymit.projectdonut.jsonconvertion.time;

import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.time.TimeDelta;

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
    public TimeDelta fromJson(String json) {
        return new TimeDelta(new JSONObject(json).getLong(TIME_DELTA_TAG));
    }
}
