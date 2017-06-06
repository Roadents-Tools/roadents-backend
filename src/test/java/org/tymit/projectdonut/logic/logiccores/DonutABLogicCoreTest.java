package org.tymit.projectdonut.logic.logiccores;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.costs.BulkCostArgs;
import org.tymit.projectdonut.costs.CostCalculator;
import org.tymit.projectdonut.costs.providers.routing.RouteCostProvider;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.routing.TravelRoute;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.StationRetriever;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 5/25/17.
 */
public class DonutABLogicCoreTest {

    private static final double[] CENTER = new double[] { 37.358658, -122.008763 };
    private static final StartPoint start = new StartPoint(CENTER);
    private static final double[] AMES = new double[] { 37.3840186, -122.088272 };
    private static final LocationType randType = new LocationType("TestType", "TestType");
    private static final List<DestinationLocation> testDestinations = Lists.newArrayList(
            new DestinationLocation("D1", randType, AMES)
    );
    private static boolean runTest = false;
    TimePoint startTime = new TimePoint(1495713600L * 1000L, "GMT");

    @Before
    public void setup() {
        LoggingUtils.setPrintImmediate(true);
        StationRetriever.setTestMode(true);
    }

    @Test
    public void runDonutRouting() throws Exception {
        if (!runTest) return;
        List<TravelRoute> donutRoutes = (new DonutABLogicCore()).runDonutRouting(start, startTime, testDestinations);
        Assert.assertEquals(testDestinations.size(), donutRoutes.size());

        BulkCostArgs args = new BulkCostArgs()
                .setCostTag(RouteCostProvider.TAG)
                .setArg(RouteCostProvider.POINT_ONE_TAG, start)
                .setArg(RouteCostProvider.START_TIME_TAG, startTime);
        testDestinations.forEach(args::addSubject);

        Map<Object, Object> destToDelta = CostCalculator.getCostValue(args);
        for (TravelRoute rt : donutRoutes) {
            TimeDelta expected = (TimeDelta) destToDelta.getOrDefault(rt.getDestination(), new TimeDelta(-1));
            Assert.assertEquals(expected, rt.getTotalTime());
        }
    }

}