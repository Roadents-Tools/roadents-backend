package org.tymit.projectdonut.stations;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.stations.database.StationDbHelper;

import java.util.Iterator;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class StationRetriever {
    public static List<TransStation> getStations(double[] center, double range, TransChain chain, List<CostArgs> args) {
        List<TransStation> allStations = StationDbHelper.getHelper().queryStations(center, range, chain);

        if (args == null || args.size() == 0) return allStations;
        Iterator<TransStation> stationIterator = allStations.iterator();
        while (stationIterator.hasNext()) {
            for (CostArgs arg : args) {
                arg.setSubject(stationIterator.next());
                if (!CostCalculator.isWithinCosts(arg)) stationIterator.remove();
            }
        }
        return allStations;
    }
}
