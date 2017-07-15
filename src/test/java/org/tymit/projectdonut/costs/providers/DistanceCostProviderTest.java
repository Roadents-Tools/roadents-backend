package org.tymit.projectdonut.costs.providers;

import org.junit.Assert;
import org.junit.Test;
import org.tymit.projectdonut.costs.arguments.CostArgs;
import org.tymit.projectdonut.costs.providers.distance.DistanceCostProvider;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.utils.LocationUtils;

/**
 * Created by ilan on 7/8/16.
 */
public class DistanceCostProviderTest {

    private static final double[] testp1 = new double[]{1, 1};
    private static final double[] testp2 = new double[]{1, 2};
    private static final DestinationLocation testp1obj = new DestinationLocation(null, null, testp1);
    private static final DestinationLocation testp2obj = new DestinationLocation(null, null, testp2);

    private static final String[] VALID_COMPARISONS = new String[]{"==", "<", ">", "<=", ">="};

    private static final DistanceCostProvider PROVIDER = new DistanceCostProvider();


    @Test
    public void isWithinCosts() throws Exception {
        CostArgs testArgs = new CostArgs();
        testArgs.setCostTag(DistanceCostProvider.TAG);
        testArgs.setSubject(testp1);
        testArgs.getArgs().put(DistanceCostProvider.POINT_TWO_TAG, testp2);

        //Greater than (or equal to)
        double compareValue = Double.MAX_VALUE;
        testArgs.getArgs().put(DistanceCostProvider.COMPARE_VALUE_TAG, compareValue);
        for (String comparisonValue : VALID_COMPARISONS) {
            testArgs.getArgs().put(DistanceCostProvider.COMPARISON_TAG, comparisonValue);
            boolean isWithinCosts = PROVIDER.isWithinCosts(testArgs);
            Assert.assertEquals(comparisonValue.contains("<"), isWithinCosts);
        }

        //Less than (or equal to)
        compareValue = Double.MIN_VALUE;
        testArgs.getArgs().put(DistanceCostProvider.COMPARE_VALUE_TAG, compareValue);
        for (String comparisonValue : VALID_COMPARISONS) {
            testArgs.getArgs().put(DistanceCostProvider.COMPARISON_TAG, comparisonValue);
            boolean isWithinCosts = PROVIDER.isWithinCosts(testArgs);
            Assert.assertEquals(comparisonValue.contains(">"), isWithinCosts);
        }

        //Equal to
        compareValue = LocationUtils.distanceBetween(testp1, testp2, true);
        testArgs.getArgs().put(DistanceCostProvider.COMPARE_VALUE_TAG, compareValue);
        for (String comparisonValue : VALID_COMPARISONS) {
            testArgs.getArgs().put(DistanceCostProvider.COMPARISON_TAG, comparisonValue);
            boolean isWithinCosts = PROVIDER.isWithinCosts(testArgs);
            Assert.assertEquals(comparisonValue.contains("="), isWithinCosts);
        }

    }

    @Test
    public void getCostValue() throws Exception {

        CostArgs testArgs = new CostArgs();
        testArgs.setCostTag(DistanceCostProvider.TAG);
        Object[] allp1 = new Object[]{testp1, testp1obj};
        Object[] allp2 = new Object[]{testp2, testp2obj};

        for (Object p1 : allp1) {
            for (Object p2 : allp2) {
                testArgs.setSubject(p1);
                testArgs.getArgs().put(DistanceCostProvider.POINT_TWO_TAG, p2);
                double expectedDistance = (Double) PROVIDER.getCostValue(testArgs);
                Assert.assertTrue(expectedDistance > 0);

                testArgs.getArgs().put(DistanceCostProvider.POINT_TWO_TAG, p1);
                Assert.assertEquals(0.0, PROVIDER.getCostValue(testArgs));

                testArgs.setSubject(p2);
                Assert.assertEquals(expectedDistance, PROVIDER.getCostValue(testArgs));

                testArgs.getArgs().put(DistanceCostProvider.POINT_TWO_TAG, p2);
                Assert.assertEquals(0.0, PROVIDER.getCostValue(testArgs));

            }
        }
    }

}