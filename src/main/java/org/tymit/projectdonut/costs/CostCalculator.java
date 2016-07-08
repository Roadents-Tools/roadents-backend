package org.tymit.projectdonut.costs;

import org.tymit.projectdonut.costs.providers.CostProviderHelper;

/**
 * Created by ilan on 7/7/16.
 */
public class CostCalculator {
    public static Object getCostValue(CostArgs args) {
        return CostProviderHelper.getHelper().getCostValue(args);
    }

    public static boolean isWithinCosts(CostArgs args) {
        return CostProviderHelper.getHelper().isWithinCosts(args);
    }
}
