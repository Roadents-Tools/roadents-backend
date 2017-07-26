package org.tymit.projectdonut.stations;

import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.costs.arguments.CostArgs;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.helpers.StationChainCacheHelper;
import org.tymit.projectdonut.stations.helpers.StationDbHelper;
import org.tymit.projectdonut.stations.helpers.StationDbUpdater;
import org.tymit.projectdonut.stations.memory.ChainStore;
import org.tymit.projectdonut.stations.memory.StationToChainStore;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public class StationRetriever {


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
                    .queryStations(center, range, startTime, maxDelta, chain);
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

    public static List<TransStation> getChain(TransChain transChain, List<CostArgs> args) {

        List<TransStation> inCache = ChainStore.getStore().getChain(transChain.getName());
        if (inCache != null && !inCache.isEmpty()) return inCache;

        List<TransStation> allStations = StationChainCacheHelper.getHelper()
                .getCachedStations(null, -1, null, null, transChain);
        if (allStations == null || allStations.isEmpty()) {
            allStations = StationDbHelper.getHelper()
                    .queryStations(null, -1, null, null, transChain);
            ChainStore.getStore().putChain(transChain, allStations);
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

    public static List<TransStation> getChainsForStation(double[] coords, List<CostArgs> args) {
        List<TransStation> allStations = StationToChainStore.getInstance().getChainsForStation(coords[0], coords[1]);
        if (allStations != null && !allStations.isEmpty()) return allStations;

        allStations = StationDbHelper.getHelper()
                .queryStations(coords, LocationUtils.metersToMiles(.1), null, null, null);
        if (allStations != null && !allStations.isEmpty()) {
            StationToChainStore.getInstance().putStation(coords[0], coords[1], allStations);
        } else {
            return Collections.emptyList();
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

    public static List<TransStation> getStrippedStations(double[] center, double range, int limit, List<CostArgs> args) {
        List<TransStation> allStations = StationDbHelper.getHelper().queryStrippedStations(center, range, limit);

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

    public static void setTestMode(boolean testMode) {
        StationDbUpdater.setTestMode(testMode);
        StationDbHelper.setTestMode(testMode);
        StationChainCacheHelper.setTestMode(testMode);
    }

    public static void prepareArea(double[] center, double range, TimePoint startTime, TimeDelta maxDelta) {
        List<TransStation> allStations = StationChainCacheHelper.getHelper()
                .getCachedStations(center, range, startTime, maxDelta, null);
        if (allStations == null || allStations.isEmpty()) {
            allStations = StationDbHelper.getHelper().queryStations(center, range, startTime, maxDelta, null);
            StationChainCacheHelper.getHelper().cacheStations(center, range, startTime, maxDelta, allStations);
        }
    }
}
