package org.tymit.projectdonut.costs.providers;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtil;

import java.util.Collection;
import java.util.Set;

public class DistanceCostProvider implements CostProvider {

    /**
     * Tags
     **/
    public static final String COMPARISON_TAG = "comparison";
    public static final String COMPARE_VALUE_TAG = "compareto";
    public static final String UNIT_TAG = "unit";
    public static final String POINT_TWO_TAG = "p2";

    public static final String TAG = "distance";
    private static final String[] CACHEABLE = new String[]{"latlong"};

    private static final double ERROR_MARGIN = 0.001; //We use fuzzy equals


    public String getTag() {
        return TAG;
    }

    public Set<String> getCacheableValueTags() {
        return Sets.newHashSet(CACHEABLE);
    }

    public boolean isWithinCosts(CostArgs arg) {

        Double compareTo = (Double) arg.getArgs().get(COMPARE_VALUE_TAG);
        String comparison = (String) arg.getArgs().get(COMPARISON_TAG);
        if (compareTo == null || comparison == null) {
            LoggingUtil.logError(this.getClass().getName(), "Null compare values. ");
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
                LoggingUtil.logError(this.getClass().getName(), "Invalid Comparison: " + comparison);
                return true;
        }
    }

    public Object getCostValue(CostArgs arg) {
        if (arg.getSubject() == null || arg.getArgs().get(POINT_TWO_TAG) == null
                || arg.getSubject() == arg.getArgs().get(POINT_TWO_TAG)) {
            LoggingUtil.logError(this.getClass().getName(), "Null coordinate values. ");
            return 0.0; //We default to zero on error.
        }


        double[] subj = extractCoords(arg.getSubject());
        double[] oth = extractCoords(arg.getArgs().get(POINT_TWO_TAG));
        if (subj == null || oth == null) {
            LoggingUtil.logError(this.getClass().getName(), "Could not extract coordinate values. ");
            return 0.0;
        }

        String unitArg = (String) arg.getArgs().get(UNIT_TAG);

        //We default to miles, so we only have to check if we have any sort of metric requests, ie "Kilometer", or "kM", or "kiLOmEt", etc.
        boolean miles = unitArg == null || !(unitArg.toLowerCase().contains("k") || unitArg.toLowerCase().contains("meter"));

        return LocationUtils.distanceBetween(subj, oth, miles);
    }

    private static double[] extractCoords(Object obj) {

        if (obj instanceof LocationPoint) return ((LocationPoint) obj).getCoordinates();

        if (obj instanceof double[]) return (double[]) obj;

        if (obj instanceof Double[]) {
            double[] out = new double[((Double[]) obj).length];
            for (int i = 0; i < ((Double[]) obj).length; i++) {
                out[i] = ((Double[]) obj)[i].doubleValue();
            }
            return out;
        }

        if (obj instanceof Collection<?>) {
            int argSize = ((Collection) obj).size();

        }

        return null;
    }

}