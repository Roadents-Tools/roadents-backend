package org.tymit.projectdonut.logic;

import org.tymit.projectdonut.locations.LocationRetriever;
import org.tymit.projectdonut.logic.logiccores.LogicCoreHelper;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationRunner {

    private static String TEST_KEY = "test";

    public static Map<String, List<Object>> runApplication(String tag, Map<String, Object> args) {
        if ((boolean) args.getOrDefault(TEST_KEY, false)) {
            LocationRetriever.setTestMode(true);
            StationRetriever.setTestMode(true);
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
