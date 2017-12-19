package com.reroute.backend.logic.helpers;

import com.google.common.collect.Lists;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.interfaces.LogicCore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogicCoreHelper {

    private static final ApplicationResult NO_CORE_FOUND = ApplicationResult.err(Lists.newArrayList(new CoreNotFoundException()));

    private static final LogicCore[] allCores = initializeCoresList();

    private static final LogicCoreHelper instance = new LogicCoreHelper();

    private Map<String, LogicCore> coreMap;

    private LogicCoreHelper() {
        initializeCoreMap();
    }

    private void initializeCoreMap() {
        coreMap = new ConcurrentHashMap<>();
        for (LogicCore core : allCores) {
            for (String tag : core.getTags()) {
                coreMap.put(tag, core);
            }
        }
    }

    public static LogicCoreHelper getHelper() {
        return instance;
    }

    private static LogicCore[] initializeCoresList() {
        return new LogicCore[] {
        };
    }

    public ApplicationResult runCore(ApplicationRequest request) {
        LogicCore toRun = coreMap.get(request.getTag());
        if (toRun == null) {
            return NO_CORE_FOUND;
        }
        if (!toRun.isValid(request)) {
            return ApplicationResult.err(Lists.newArrayList(new InvalidRequestException()));
        }
        return coreMap.get(request.getTag()).performLogic(request);
    }

    public static class CoreNotFoundException extends Exception {
    }

    public static class InvalidRequestException extends IllegalArgumentException {
    }
}
