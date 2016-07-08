package org.tymit.projectdonut.logic.logiccores;

import java.util.List;
import java.util.Map;

public interface LogicCore {
    Map<String, List<Object>> performLogic(List<Object> args);

    String getTag();
}
