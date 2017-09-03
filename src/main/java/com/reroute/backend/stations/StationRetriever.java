package com.reroute.backend.stations;

import com.reroute.backend.costs.CostCalculator;
import com.reroute.backend.costs.arguments.CostArgs;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.helpers.StationChainCacheHelper;
import com.reroute.backend.stations.helpers.StationDbHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public final class StationRetriever {

    private StationRetriever() {
    }


    private static <T> List<T> filterList(List<T> toFilter, List<CostArgs> args) {

        if (args == null || args.size() == 0) return toFilter;

        Predicate<T> costPredicate = tval -> args.stream()
                .allMatch(arg -> CostCalculator.isWithinCosts(arg.setSubject(tval)));

        return toFilter.stream()
                .filter(costPredicate)
                .collect(Collectors.toList());

    }

    public static void setTestMode(boolean testMode) {
        StationDbHelper.setTestMode(testMode);
        StationChainCacheHelper.setTestMode(testMode);
    }

    public static List<TransStation> getStationsInArea(LocationPoint center, Distance range, List<CostArgs> args) {
        List<TransStation> allStations = StationChainCacheHelper.getHelper().getStationsInArea(center, range);

        if (allStations != null && !allStations.isEmpty()) return filterList(allStations, args);
        allStations = StationDbHelper.getHelper()
                .getStationsInArea(center, range);

        if (allStations == null || allStations.isEmpty()) {
            return Collections.emptyList();
        }

        StationChainCacheHelper.getHelper().putArea(center, range, allStations);

        return filterList(allStations, args);
    }
    public static Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station, List<CostArgs> args) {

        Map<TransChain, List<SchedulePoint>> rval = StationChainCacheHelper.getHelper()
                .getChainsForStation(station);

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getChainsForStation(station);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }
    public static Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {

        Map<TransStation, TimeDelta> rval = StationChainCacheHelper.getHelper()
                .getArrivableStations(chain, startTime, maxDelta);

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getArrivableStations(chain, startTime, maxDelta);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }

    public static boolean prepareWorld(WorldInfo request) {
        if (StationChainCacheHelper.getHelper().hasWorld(request)) {
            return true;
        }
        Map<TransChain, Map<TransStation, List<SchedulePoint>>> world = StationDbHelper.getHelper().getWorld(request);
        return StationChainCacheHelper.getHelper().putWorld(request, world);
    }
}
