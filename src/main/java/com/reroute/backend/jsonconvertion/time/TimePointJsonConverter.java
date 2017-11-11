package com.reroute.backend.jsonconvertion.time;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.model.time.TimePoint;
import org.json.JSONObject;

/**
 * Created by ilan on 6/3/17.
 */
public class TimePointJsonConverter implements JsonConverter<TimePoint> {
    private static final String UNIX_TIME_TAG = "time";
    private static final String TIME_ZONE_TAG = "timezone";

    @Override
    public String toJson(TimePoint input) {
        return String.format(
                "{\"%s\" : %d, \"%s\" : \"%s\"}",
                UNIX_TIME_TAG, input.getUnixTime(),
                TIME_ZONE_TAG, input.getTimeZone()
        );
    }

    @Override
    public JSONObject toJsonObject(TimePoint input) {
        return new JSONObject()
                .put(UNIX_TIME_TAG, input.getUnixTime())
                .put(TIME_ZONE_TAG, input.getTimeZone());
    }

    @Override
    public TimePoint fromJsonObject(JSONObject obj) {
        return TimePoint.from(obj.getLong(UNIX_TIME_TAG), obj.getString(TIME_ZONE_TAG));
    }
}
