package org.tymit.projectdonut.jsonconvertion.time;

import com.google.common.collect.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.time.SchedulePoint;

/**
 * Created by ilan on 6/3/17.
 */
public class SchedulePointJsonConverter implements JsonConverter<SchedulePoint> {

    private static final String HOUR_TAG = "hour";
    private static final String MINUTE_TAG = "minute";
    private static final String SECOND_TAG = "second";
    private static final String FUZZ_TAG = "fuzz";
    private static final String VALID_DAYS_ARRAY = "valid_days";

    @Override
    public String toJson(SchedulePoint input) {
        JSONObject obj = new JSONObject();
        obj.put(HOUR_TAG, input.getHour());
        obj.put(MINUTE_TAG, input.getMinute());
        obj.put(SECOND_TAG, input.getSecond());
        obj.put(FUZZ_TAG, input.getFuzz());
        obj.put(VALID_DAYS_ARRAY, Lists.newArrayList(input.getValidDays()));
        return obj.toString();
    }

    @Override
    public SchedulePoint fromJson(String json) {
        JSONObject obj = new JSONObject(json);

        int hour = obj.getInt(HOUR_TAG);
        int min = obj.getInt(MINUTE_TAG);
        int sec = obj.getInt(SECOND_TAG);
        long fuzz = obj.getLong(FUZZ_TAG);

        JSONArray validDaysJson = obj.getJSONArray(VALID_DAYS_ARRAY);
        boolean[] validDays = new boolean[7];
        for (int i = 0; i < 7; i++) {
            validDays[i] = validDaysJson.getBoolean(i);
        }

        return new SchedulePoint(hour, min, sec, validDays, fuzz);
    }
}
