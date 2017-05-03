package org.tymit.projectdonut.costs.providers;

import org.tymit.projectdonut.costs.CostArgs;

/**
 * Created by ilan on 5/2/17.
 */
public class TimeCostProvider implements CostProvider {

    private static String TAG = "atobtime";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean isWithinCosts(CostArgs arg) {
        return false;
    }

    @Override
    public Object getCostValue(CostArgs arg) {
        return null;
    }
}
