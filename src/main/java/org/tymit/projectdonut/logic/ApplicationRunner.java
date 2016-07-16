package org.tymit.projectdonut.logic;

import org.tymit.projectdonut.logic.logiccores.LogicCoreHelper;

import java.util.List;
import java.util.Map;

public class ApplicationRunner {

    public static Map<String, List<Object>> runApplication(String tag, Map<String, Object> args) {
        return LogicCoreHelper.getHelper().runCore(tag, args);
    }

}
