package org.tymit.projectdonut.stations;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.caches.StationChainCacheHelper;
import org.tymit.projectdonut.stations.database.StationDbHelper;
import org.tymit.projectdonut.stations.updates.StationDbUpdater;

import java.util.Iterator;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class StationRetriever {


    @Deprecated
    public static List<TransStation> getStations(double[] center, double range, TransChain chain, List<CostArgs> args) {
        return getStations(center, range, null, null, chain, args);
    }

    public static List<TransStation> getStations(double[] center, double range,
                                                 TimePoint startTime, TimeDelta maxDelta,
                                                 TransChain chain, List<CostArgs> args) {
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

    @Deprecated
    public static List<TransStation> getStations(TimePoint startTime, TimeDelta range, TransChain chain, List<CostArgs> args) {
        return getStations(null, -1, startTime, range, chain, args);
    }

    public static void setTestMode(boolean testMode) {
        StationDbUpdater.setTestMode(testMode);
        StationDbHelper.setTestMode(testMode);
        StationChainCacheHelper.setTestMode(testMode);
    }
}
