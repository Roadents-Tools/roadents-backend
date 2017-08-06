package com.reroute.backend.logic.helpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.reroute.backend.logic.calculator.CalculatorCore;
import com.reroute.backend.logic.finder.FinderCore;
import com.reroute.backend.logic.generator.GeneratorCore;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.pathmaker.PathmakerCore;

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
        return new LogicCore[] {
                new GeneratorCore(), new PathmakerCore(),
                new CalculatorCore(), new FinderCore()
        };
    }

    public Map<String, List<Object>> runCore(String coreTag, Map<String, Object> args) {
        if (coreMap.get(coreTag) == null) {
            return NO_CORE_FOUND;
        }
        return coreMap.get(coreTag).performLogic(args);
    }
}
