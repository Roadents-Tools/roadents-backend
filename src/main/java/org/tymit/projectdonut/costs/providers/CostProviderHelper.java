package org.tymit.projectdonut.costs.providers;

import org.tymit.projectdonut.costs.CostArgs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/7/16.
 */
public class CostProviderHelper {

    private static final CostProvider[] allProviders = initializeProvidersList();
    private static CostProviderHelper instance = new CostProviderHelper();

    private Map<String, CostProvider> tagToProvider = new ConcurrentHashMap<>(allProviders.length);

    private CostProviderHelper() {
        initializeProviderMap();
    }

    private void initializeProviderMap() {
        for (CostProvider provider : allProviders) {
            tagToProvider.put(provider.getTag(), provider);
        }
    }

    private static CostProvider[] initializeProvidersList() {
        return new CostProvider[]{new DistanceCostProvider()};
    }

    public static CostProviderHelper getHelper() {
        return instance;
    }

    public boolean isWithinCosts(CostArgs args) {
        if (args == null || args.getCostTag() == null || tagToProvider.get(args.getCostTag()) == null) return true;

        return tagToProvider.get(args.getCostTag()).isWithinCosts(args);

    }

    public Object getCostValue(CostArgs args) {
        return null;
    }

}
