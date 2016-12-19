package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogicCoreHelper {

    private static final LogicCore[] allCores = initializeCoresList();
    private static final Map<String, List<Object>> NO_CORE_FOUND = ImmutableMap.of("ERROR", ImmutableList.of("No logic core found."));

    private static final LogicCoreHelper instance = new LogicCoreHelper();

    private Map<String, LogicCore> coreMap;

    private LogicCoreHelper() {
        initializeCoreMap();
    }

    private void initializeCoreMap() {
        coreMap = new ConcurrentHashMap<>();
        for (LogicCore core : allCores) {
            coreMap.put(core.getTag(), core);
        }
    }

    public static LogicCoreHelper getHelper() {
        return instance;
    }

    private static LogicCore[] initializeCoresList() {
        return new LogicCore[]{new DonutLogicCore()};
    }

    public Map<String, List<Object>> runCore(String coreTag, Map<String, Object> args) {
        if (coreMap.get(coreTag) == null) {
            return NO_CORE_FOUND;
        }
        return coreMap.get(coreTag).performLogic(args);
    }
}
