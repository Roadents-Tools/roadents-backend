package org.tymit.projectdonut.costs.providers;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.TimeDelta;

import java.util.function.Predicate;

/**
 * Created by ilan on 5/2/17.
 */
public class TimeCostProvider implements CostProvider {

    /*
    PARAM:
    Subject: a LocationPoint or double[] representing the point to start at
    Args:
     - starttime = the TimePoint or unixtime in milliseconds since the unix epoch to start at
     - p2 = the LocationPoint or double[] to travel to
     - compareTo = the TimeDelta or millisecond long to compare the time between A and B to
     - comparison = the comparison to make in an isWithinCost call,
       either >,<,>=,<=,==, or !=.
     */

    public static final String TAG = "time";
    public static final String START_TIME_TAG = "starttime";
    public static final String COMPARISON_TAG = "comparison";
    public static final String COMPARE_VALUE_TAG = "compareto";
    public static final String POINT_TWO_TAG = "p2";

    /**
     * Argument extraction methods
     **/
    private static double[] extractStart(CostArgs args) {
        double[] rval = null;
        Object startArg = args.getSubject();

        if (startArg instanceof double[]) rval = (double[]) startArg;

        else if (startArg instanceof LocationPoint) rval = ((LocationPoint) startArg).getCoordinates();

        else if (startArg instanceof Double[]) {
            Double[] lclstartArg = (Double[]) startArg;
            rval = new double[lclstartArg.length];
            for (int i = 0; i < lclstartArg.length; i++) {
                rval[i] = lclstartArg[i];
            }
        }

        return rval;
    }

    private static double[] extractEnd(CostArgs args) {
        double[] rval = null;
        Object endArg = args.getArgs().get(POINT_TWO_TAG);

        if (endArg instanceof double[]) rval = (double[]) endArg;

        else if (endArg instanceof LocationPoint) rval = ((LocationPoint) endArg).getCoordinates();

        else if (endArg instanceof Double[]) {
            Double[] lclendArg = (Double[]) endArg;
            rval = new double[lclendArg.length];
            for (int i = 0; i < lclendArg.length; i++) {
                rval[i] = lclendArg[i];
            }
        }

        return rval;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean isWithinCosts(CostArgs arg) {
        return extractComparison(arg).test((TimeDelta) getCostValue(arg));
    }

    @Override
    public Object getCostValue(CostArgs arg) {
        return null;
    }

    @Override
    public boolean isUp() {
        return true; //TODO
    }

    private static Predicate<TimeDelta> extractComparison(CostArgs args) {

        long border = extractCompareBorder(args);
        Object compTagArg = args.getArgs().get(COMPARISON_TAG);

        if (compTagArg instanceof Character) {
            switch ((Character) compTagArg) {
                case '<':
                    return dt -> dt.getDeltaLong() < border;
                case '>':
                    return dt -> dt.getDeltaLong() > border;
            }
        }

        if (compTagArg instanceof String) {
            switch ((String) compTagArg) {
                case "<":
                    return dt -> dt.getDeltaLong() < border;
                case "<=":
                    return dt -> dt.getDeltaLong() <= border;
                case "=<": //Just in case
                    return dt -> dt.getDeltaLong() <= border;

                case ">":
                    return dt -> dt.getDeltaLong() > border;
                case ">=":
                    return dt -> dt.getDeltaLong() >= border;
                case "=>": //Just in case
                    return dt -> dt.getDeltaLong() >= border;

                case "==":
                    return dt -> dt.getDeltaLong() == border;
                case "===": //For the Javascript devs
                    return dt -> dt.getDeltaLong() == border;
                case "=":   //For the typos
                    return dt -> dt.getDeltaLong() == border;

                case "!=":
                    return dt -> dt.getDeltaLong() != border;
                case "!==":
                    return dt -> dt.getDeltaLong() != border;
            }
        }

        return a -> true;
    }

    private static long extractCompareBorder(CostArgs args) {
        Object rvalArg = args.getArgs().get(COMPARE_VALUE_TAG);
        if (rvalArg instanceof Long) return (Long) rvalArg;
        else if (rvalArg instanceof TimeDelta) return ((TimeDelta) rvalArg).getDeltaLong();
        return 0;
    }


}
