package org.tymit.projectdonut.costs.providers.distance;

import org.tymit.projectdonut.costs.arguments.CostArgs;
import org.tymit.projectdonut.costs.interfaces.CostProvider;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;

public class DistanceCostProvider implements CostProvider {

    /**
     * Tags
     **/
    public static final String COMPARISON_TAG = "comparison";
    public static final String COMPARE_VALUE_TAG = "compareto";
    public static final String POINT_TWO_TAG = "p2";

    public static final String TAG = "distance";

    private static final double ERROR_MARGIN = 0.0001; //We use fuzzy equals


    public String getTag() {
        return TAG;
    }

    public boolean isWithinCosts(CostArgs arg) {

        Double compareTo = (Double) arg.getArgs().get(COMPARE_VALUE_TAG);
        String comparison = (String) arg.getArgs().get(COMPARISON_TAG);
        if (compareTo == null || comparison == null) {
            LoggingUtils.logMessage(this.getClass().getName(), "Null compare values. ");
            return true; //Bad requests are ignored
        }

        Double currentVal = (Double) getCostValue(arg);

        switch (comparison) {
            case ">=":
                return currentVal >= compareTo;
            case "<=":
                return currentVal <= compareTo;
            case "<":
                return currentVal < compareTo;
            case ">":
                return currentVal > compareTo;
            case "=":
            case "==":
            case "===":
                return Math.abs(currentVal - compareTo) < ERROR_MARGIN;
            default:
                LoggingUtils.logMessage(this.getClass().getName(), "Invalid Comparison: " + comparison);
                return true;
        }
    }

    public Object getCostValue(CostArgs arg) {
        if (arg.getSubject() == null || arg.getArgs().get(POINT_TWO_TAG) == null
                || arg.getSubject() == arg.getArgs().get(POINT_TWO_TAG)) {
            LoggingUtils.logMessage(this.getClass().getName(), "Null coordinate values. ");
            return 0.0; //We default to zero on error.
        }


        double[] subj = extractCoords(arg.getSubject());
        double[] oth = extractCoords(arg.getArgs().get(POINT_TWO_TAG));
        if (subj == null || oth == null) {
            LoggingUtils.logMessage(this.getClass().getName(), "Could not extract coordinate values. ");
            return 0.0;
        }

        return LocationUtils.distanceBetween(new StartPoint(subj), new StartPoint(oth));
    }

    @Override
    public boolean isUp() {
        return true;
    }

    private static double[] extractCoords(Object obj) {

        if (obj instanceof LocationPoint) return ((LocationPoint) obj).getCoordinates();

        if (obj instanceof double[]) return (double[]) obj;

        if (obj instanceof Double[]) {
            return Arrays.stream((Double[]) obj)
                    .mapToDouble(aDouble -> aDouble)
                    .toArray();
        }

        return null;
    }

}
