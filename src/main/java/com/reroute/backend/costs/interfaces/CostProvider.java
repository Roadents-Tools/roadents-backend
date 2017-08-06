package com.reroute.backend.costs.interfaces;

import com.reroute.backend.costs.arguments.CostArgs;

/**
 * Created by ilan on 7/7/16.
 */
public interface CostProvider {
    String getTag();

    boolean isWithinCosts(CostArgs arg);

    Object getCostValue(CostArgs arg);

    boolean isUp();
}
