package org.tymit.projectdonut.stations;

import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.costs.arguments.CostArgs;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.distance.DistanceUnits;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.helpers.StationChainCacheHelper;
import org.tymit.projectdonut.stations.helpers.StationDbHelper;
import org.tymit.projectdonut.stations.helpers.StationDbUpdater;

import java.util.Collections;
import java.util.Iterator;
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

    @Deprecated
    public static List<TransStation> getStations(double[] center, double range,
                                                 TimePoint startTime, TimeDelta maxDelta,
                                                 TransChain chain, List<CostArgs> args) {

        if (chain != null && center == null) {
            return getChain(chain, args).stream()
                    .filter(station -> startTime.timeUntil(station.getNextArrival(startTime)).getDeltaLong() <= maxDelta
                            .getDeltaLong())
                    .collect(Collectors.toList());
        }

        if (startTime == null && maxDelta == null && center != null && range <= .0001) {
            return getChainsForStation(center, args);
        }

        List<TransStation> allStations = StationChainCacheHelper.getHelper()
                .getCachedStations(center, range, startTime, maxDelta, chain);
        if (allStations == null || allStations.isEmpty()) {
            allStations = StationDbHelper.getHelper()
                    .queryStations(new StartPoint(center), new Distance(range, DistanceUnits.MILES), startTime, maxDelta, chain);
            if (chain == null) StationChainCacheHelper.getHelper()
                    .cacheStations(center, range, startTime, maxDelta, allStations);
        }

        if (args == null || args.size() == 0) return allStations;
        Iterator<TransStation> stationIterator = allStations.iterator();
        while (stationIterator.hasNext()) {
            for (CostArgs arg : args) {
                arg.setSubject(stationIterator.next());
                if (!CostCalculator.isWithinCosts(arg))
                    stationIterator.remove();
            }
        }
        return allStations;
    }

    @Deprecated
    public static List<TransStation> getChain(TransChain transChain, List<CostArgs> args) {

        List<TransStation> inCache = null;
        if (inCache != null && !inCache.isEmpty()) return inCache;

        List<TransStation> allStations = StationChainCacheHelper.getHelper()
                .getCachedStations(null, -1, null, null, transChain);
        if (allStations == null || allStations.isEmpty()) {
            allStations = StationDbHelper.getHelper()
                    .queryStations(null, new Distance(-1, DistanceUnits.METERS), null, null, transChain);
        }

        return filterList(allStations, args);
    }

    private static <T> List<T> filterList(List<T> toFilter, List<CostArgs> args) {

        if (args == null || args.size() == 0) return toFilter;

        Predicate<T> costPredicate = tval -> args.stream()
                .allMatch(arg -> CostCalculator.isWithinCosts(arg.setSubject(tval)));

        return toFilter.stream()
                .filter(costPredicate)
                .collect(Collectors.toList());

    }

    @Deprecated
    public static List<TransStation> getChainsForStation(double[] coords, List<CostArgs> args) {
        List<TransStation> allStations = null;
        if (allStations != null && !allStations.isEmpty()) return allStations;

        allStations = StationDbHelper.getHelper()
                .queryStations(new StartPoint(coords), new Distance(0, DistanceUnits.METERS), null, null, null);
        if (allStations == null || allStations.isEmpty()) {
            return Collections.emptyList();
        }

        return filterList(allStations, args);
    }

    public static void setTestMode(boolean testMode) {
        StationDbUpdater.setTestMode(testMode);
        StationDbHelper.setTestMode(testMode);
        StationChainCacheHelper.setTestMode(testMode);
    }

    @Deprecated
    public static void prepareArea(double[] center, double range, TimePoint startTime, TimeDelta maxDelta) {
        List<TransStation> allStations = StationChainCacheHelper.getHelper()
                .getCachedStations(center, range, startTime, maxDelta, null);
        if (allStations == null || allStations.isEmpty()) {
            allStations = StationDbHelper.getHelper()
                    .queryStations(new StartPoint(center), new Distance(range, DistanceUnits.MILES), startTime, maxDelta, null);
            StationChainCacheHelper.getHelper().cacheStations(center, range, startTime, maxDelta, allStations);
        }
    }

    @Deprecated
    public static List<TransStation> getStrippedStations(double[] center, double range, int limit, List<CostArgs> args) {
        List<TransStation> allStations = StationDbHelper.getHelper()
                .queryStrippedStations(new StartPoint(center), new Distance(range, DistanceUnits.MILES), limit);
        return filterList(allStations, args);
    }


    //Post V2
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
        Map<LocationPoint, List<TransStation>> allStations = null;

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

        Map<TransChain, List<SchedulePoint>> rval = null;

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getChainsForStation(station);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }

    public static Map<TransStation, Map<TransChain, List<SchedulePoint>>> getChainsForStations(List<TransStation> stations) {
        Map<TransStation, Map<TransChain, List<SchedulePoint>>> rval = null;

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

        Map<TransChain, Map<TransStation, TimeDelta>> rval = null;

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getArrivableStations(chains, startTime, maxDelta);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }

    public static Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(Map<TransChain, TimeDelta> chainsAndExtras, TimePoint generalStart, TimeDelta maxDelta) {

        Map<TransChain, Map<TransStation, TimeDelta>> rval = null;

        if (rval != null && !rval.isEmpty()) return rval;
        rval = StationDbHelper.getHelper()
                .getArrivableStations(chainsAndExtras, generalStart, maxDelta);

        if (rval == null || rval.isEmpty()) {
            return Collections.emptyMap();
        }

        return rval;
    }

    public static boolean prepareWorld(LocationPoint center, TimePoint startTime, TimeDelta maxDelta) {
        Map<TransChain, Map<TransStation, List<SchedulePoint>>> world = StationDbHelper.getHelper()
                .getWorld(center, startTime, maxDelta);
        return StationChainCacheHelper.getHelper().putWorld(world);
    }
}
