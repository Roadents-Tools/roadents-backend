package org.tymit.projectdonut.costs.providers;

import org.tymit.projectdonut.costs.CostArgs;

import java.util.Set;

/**
 * Created by ilan on 7/7/16.
 */
public interface CostProvider {
    String getTag();

    Set<String> getCacheableValueTags();

    boolean isWithinCosts(CostArgs arg);

    Object getCostValue(CostArgs arg);

}
