package org.tymit.projectdonut.costs.providers;

import org.json.JSONObject;
import org.tymit.projectdonut.model.TimeDelta;

/**
 * Created by ilan on 5/5/17.
 */
public class MapzenTimeCostProvider extends TimeCostProvider {

    public static final String[] API_KEYS = new String[] {};

    public MapzenTimeCostProvider(String apiKey) {
        super(apiKey);
    }

    @Override
    protected TimeDelta[][] extractTimeMatrix(JSONObject json) {
        return new TimeDelta[0][0];
    }

    @Override
    protected int getMaxCalls() {
        return 0;
    }

    @Override
    protected String getCallUrlFormat() {
        return null;
    }

    @Override
    protected String getLocationFormat() {
        return null;
    }

    @Override
    protected String getLocationSeparator() {
        return null;
    }
}
