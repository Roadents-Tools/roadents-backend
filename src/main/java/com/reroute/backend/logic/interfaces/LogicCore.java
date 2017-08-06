package com.reroute.backend.logic.interfaces;

import java.util.List;
import java.util.Map;

public interface LogicCore {
    Map<String, List<Object>> performLogic(Map<String, Object> args);

    String getTag();
}
