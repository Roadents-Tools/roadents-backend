package org.tymit.projectdonut.costs.providers;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/7/16.
 */
public class CostProviderHelper {

    private static final CostProviderHelper instance = new CostProviderHelper();

    private final Map<String, Set<CostProvider>> tagToProvider = new ConcurrentHashMap<>();

    private CostProviderHelper() {
        initializeProviderMap();
    }

    private void initializeProviderMap() {
        initializeProvidersList().forEach(provider -> {
            tagToProvider.putIfAbsent(provider.getTag(), Sets.newConcurrentHashSet());
            tagToProvider.get(provider.getTag()).add(provider);
        });
    }

    private static Collection<? extends CostProvider> initializeProvidersList() {
        Set<CostProvider> rval = new HashSet<>();
        rval.add(new DistanceCostProvider());

        Arrays.stream(GoogleTimeCostProvider.API_KEYS)
                .map(GoogleTimeCostProvider::new)
                .forEach(rval::add);

        Arrays.stream(MapzenTimeCostProvider.API_KEYS)
                .map(MapzenTimeCostProvider::new)
                .forEach(rval::add);

        return rval;
    }

    public static CostProviderHelper getHelper() {
        return instance;
    }

    public boolean isWithinCosts(CostArgs args) {
        //Default to true to not filter anything on error
        if (args == null || args.getCostTag() == null || tagToProvider.get(args.getCostTag()) == null) return true;

        Optional<CostProvider> foundProvider = tagToProvider.get(args.getCostTag()).stream()
                .filter(CostProvider::isUp)
                .findAny();
        if (!foundProvider.isPresent()) {
            LoggingUtils.logError(getClass().getName(), "Could not find cost with tag: %s", args.getCostTag());
            return true;
        }

        return foundProvider.get().isWithinCosts(args);

    }

    public Object getCostValue(CostArgs args) {
        //Default to 0 cost so that an invalid cost calc does not affect any cost calcs
        if (args == null || args.getCostTag() == null || tagToProvider.get(args.getCostTag()) == null) return 0;

        Optional<CostProvider> foundProvider = tagToProvider.get(args.getCostTag()).stream()
                .filter(CostProvider::isUp)
                .findAny();
        if (!foundProvider.isPresent()) {
            LoggingUtils.logError(getClass().getName(), "Could not find cost with tag: %s", args.getCostTag());
            return 0;
        }

        return foundProvider.get().getCostValue(args);
    }

}
