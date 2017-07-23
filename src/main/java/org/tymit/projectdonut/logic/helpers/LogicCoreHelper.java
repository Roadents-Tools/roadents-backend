package org.tymit.projectdonut.logic.helpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.tymit.projectdonut.logic.donut.DonutLogicCore;
import org.tymit.projectdonut.logic.donutdisplay.DonutWalkRangeLogicCore;
import org.tymit.projectdonut.logic.interfaces.LogicCore;
import org.tymit.projectdonut.logic.mole.DonutABLogicCore;
import org.tymit.projectdonut.logic.mole.MoleLogicCore;
import org.tymit.projectdonut.logic.weasel.WeaselLogicCore;

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
                new DonutLogicCore(), new DonutABLogicCore(), new DonutWalkRangeLogicCore(),
                new MoleLogicCore(), new WeaselLogicCore()
        };
    }

    public Map<String, List<Object>> runCore(String coreTag, Map<String, Object> args) {
        if (coreMap.get(coreTag) == null) {
            return NO_CORE_FOUND;
        }
        return coreMap.get(coreTag).performLogic(args);
    }
}
