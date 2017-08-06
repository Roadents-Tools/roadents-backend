package com.reroute.backend.logic;

import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.logic.helpers.LogicCoreHelper;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationRunner {

    private static final String TEST_KEY = "test";

    public static Map<String, List<Object>> runApplication(String tag, Map<String, Object> args) {
        if ((boolean) args.getOrDefault(TEST_KEY, false)) {
            LocationRetriever.setTestMode(true);
            StationRetriever.setTestMode(true);
        } else {
            LocationRetriever.setTestMode(false);
            StationRetriever.setTestMode(false);
        }
        try {
            return LogicCoreHelper.getHelper().runCore(tag, args);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            Map<String, List<Object>> rval = new HashMap<>();
            rval.put("ERROR", new ArrayList<>());
            rval.get("ERROR").add(e);
            return rval;
        }
    }

}
