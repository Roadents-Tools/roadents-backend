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
import com.reroute.backend.utils.StreamUtils;

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

    public static Map<LocationPoint, List<TransStation>> getStationsInArea(Map<LocationPoint, Distance> ranges, List<CostArgs> args) {

        Map<LocationPoint, List<TransStation>> allStations = ranges.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        Map.Entry::getKey,
                        entry -> StationChainCacheHelper.getHelper().getStationsInArea(entry.getKey(), entry.getValue())
                ));

        if (allStations != null && !allStations.isEmpty()) {
            allStations.replaceAll((key, stations) -> filterList(stations, args));
            return allStations;
        }
        allStations = StationDbHelper.getHelper()
                .getStationsInArea(ranges);

        if (allStations == null || allStations.isEmpty()) {
            return Collections.emptyMap();
        }

        allStations.forEach((key1, value) -> StationChainCacheHelper.getHelper()
                .putArea(key1, ranges.get(key1), value));
        allStations.replaceAll((key, stations) -> filterList(stations, args));

        return allStations;
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

    public static Map<TransStation, Map<TransChain, List<SchedulePoint>>> getChainsForStations(List<TransStation> stations) {

        Map<TransStation, Map<TransChain, List<SchedulePoint>>> rval = stations.stream()
                .collect(StreamUtils.collectWithValues(stat -> StationChainCacheHelper.getHelper()
                        .getChainsForStation(stat)));

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getChainsForStations(stations);

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

    public static Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(List<TransChain> chains, TimePoint startTime, TimeDelta maxDelta) {

        Map<TransChain, Map<TransStation, TimeDelta>> rval = chains.stream()
                .collect(StreamUtils.collectWithValues(chain -> StationChainCacheHelper.getHelper()
                        .getArrivableStations(chain, startTime, maxDelta)));

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getArrivableStations(chains, startTime, maxDelta);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }

    public static Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(Map<TransChain, TimeDelta> chainsAndExtras, TimePoint generalStart, TimeDelta maxDelta) {

        Map<TransChain, Map<TransStation, TimeDelta>> rval = chainsAndExtras.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        Map.Entry::getKey,
                        entry -> StationChainCacheHelper.getHelper().getArrivableStations(
                                entry.getKey(),
                                generalStart.plus(entry.getValue()),
                                maxDelta.minus(entry.getValue())
                        )
                ));

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getArrivableStations(chainsAndExtras, generalStart, maxDelta);

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
