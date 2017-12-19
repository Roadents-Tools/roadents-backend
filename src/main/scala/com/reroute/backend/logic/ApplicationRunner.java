package com.reroute.backend.logic;

import com.google.common.collect.Lists;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.logic.helpers.LogicCoreHelper;
import com.reroute.backend.stations.StationRetriever;
import com.reroute.backend.utils.LoggingUtils;

public class ApplicationRunner {

    public static ApplicationResult runApplication(ApplicationRequest args) {
        if (args.isTest()) {
            LocationRetriever.setTestMode(true);
            StationRetriever.setTestMode(true);
        } else {
            LocationRetriever.setTestMode(false);
            StationRetriever.setTestMode(false);
        }
        try {
            return LogicCoreHelper.getHelper().runCore(args);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return ApplicationResult.err(Lists.newArrayList(e));
        }
    }

}
