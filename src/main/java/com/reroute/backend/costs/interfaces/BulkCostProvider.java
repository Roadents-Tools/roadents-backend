package com.reroute.backend.costs.interfaces;

import com.reroute.backend.costs.arguments.BulkCostArgs;
import com.reroute.backend.costs.arguments.CostArgs;

import java.util.Map;

/**
 * Created by ilan on 5/5/17.
 */
public interface BulkCostProvider extends CostProvider {

    @Override
    default boolean isWithinCosts(CostArgs arg) {
        BulkCostArgs bulkArg = new BulkCostArgs(arg);
        return isWithinCosts(bulkArg).get(arg.getSubject());
    }

    Map<Object, Boolean> isWithinCosts(BulkCostArgs args);

    @Override
    default Object getCostValue(CostArgs arg) {
        BulkCostArgs bulkArg = new BulkCostArgs(arg);
        return getCostValue(bulkArg).get(arg.getSubject());
    }

    Map<Object, Object> getCostValue(BulkCostArgs args);
}
