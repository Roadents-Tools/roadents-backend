package com.reroute.backend.logic.interfaces;

import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;

import java.util.Set;

public interface LogicCore {
    ApplicationResult performLogic(ApplicationRequest request);

    Set<String> getTags();

    boolean isValid(ApplicationRequest request);

}
