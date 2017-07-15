package org.tymit.projectdonut.costs.interfaces;

import org.tymit.projectdonut.costs.arguments.CostArgs;

/**
 * Created by ilan on 7/7/16.
 */
public interface CostProvider {
    String getTag();

    boolean isWithinCosts(CostArgs arg);

    Object getCostValue(CostArgs arg);

    boolean isUp();
}
