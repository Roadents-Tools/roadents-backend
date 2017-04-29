package org.tymit.projectdonut.logic.logiccores;

import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TravelRoute;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Created by ilan on 12/24/16.
 */
public class TowardsLogicCore implements LogicCore {

    private static final String TAG = "DONUT_TOWARDS";

    @Override
    public Map<String, List<Object>> performLogic(Map<String, Object> args) {
        return null;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private Map<DestinationLocation, TravelRoute> runTowardsCore(TravelRoute baseroute, TimeDelta maxDelta, LocationType type) {

        TimeDelta[] deltas = TowardsLogicCoreSupport.getTrueDeltasPerNode(baseroute, maxDelta);

        return IntStream.range(0, deltas.length).boxed().parallel()

                //No extra time at that node, skip it
                .filter(index -> deltas[index] != null && deltas[index].getDeltaLong() > 0)

                //Get the dests surrounding each node
                .flatMap(index -> TowardsLogicCoreSupport.callDonutForRouteAtIndex(index, baseroute, deltas, type)
                        .parallelStream())

                //Collect the optimal routes to each destination, since the same dest could have multiple routes
                .collect(DonutLogicSupport.OPTIMAL_ROUTES_FOR_DESTINATIONS);
    }
}
