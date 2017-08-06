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
    public TimePoint fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        return new TimePoint(obj.getLong(UNIX_TIME_TAG), obj.getString(TIME_ZONE_TAG));
    }
}
