package com.reroute.backend.costs.providers.timing;

import com.reroute.backend.model.time.TimeDelta;
import org.json.JSONObject;

/**
 * Created by ilan on 5/5/17.
 */
public class MapzenTimeCostProvider extends TimeCostProvider {

    public static final String[] API_KEYS = new String[] {};

    public MapzenTimeCostProvider(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String buildCallUrl(double[] start, double[] end, long startTime) {
        return null;
    }

    @Override
    protected TimeDelta extractTimeDelta(JSONObject obj) {
        return null;
    }

    @Override
    protected int getMaxCalls() {
        return 0;
    }
}
