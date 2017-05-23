package org.tymit.projectdonut.costs.providers.routing;

import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.providers.CostProvider;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.model.StartPoint;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TravelRoute;

import java.util.Calendar;

/**
 * Created by ilan on 5/23/17.
 */
public abstract class RouteCostProvider implements CostProvider {

    public static final String TAG = "route";

    public static final String START_TIME_TAG = "starttime";
    public static final String COMPARISON_TAG = "comparison";
    public static final String COMPARE_VALUE_TAG = "compareto";
    public static final String POINT_TWO_TAG = "p2";
    public static final String POINT_ONE_TAG = "p1";

    public static final LocationType FILLER_TYPE = new LocationType("Destination", "Destination");

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean isWithinCosts(CostArgs arg) {
        //TODO
        return false;
    }

    @Override
    public Object getCostValue(CostArgs arg) {
        StartPoint start = extractStart(arg);
        DestinationLocation end = extractEnd(arg);
        TimePoint startTime = new TimePoint(extractStartTime(arg), "America/Los Angeles");
        return buildRoute(start, end, startTime);
    }

    protected abstract TravelRoute buildRoute(StartPoint a, DestinationLocation b, TimePoint start);

    protected static StartPoint extractStart(CostArgs args) {
        Object startArg = args.getArgs().containsKey(POINT_ONE_TAG)
                ? args.getArgs().get(POINT_ONE_TAG)
                : args.getSubject();

        return new StartPoint(normalizeLocation(startArg));
    }

    protected static double[] normalizeLocation(Object rawLocation) {
        double[] rval = null;

        if (rawLocation instanceof double[]) rval = (double[]) rawLocation;

        else if (rawLocation instanceof LocationPoint) rval = ((LocationPoint) rawLocation).getCoordinates();

        else if (rawLocation instanceof Double[]) {
            Double[] lclendArg = (Double[]) rawLocation;
            rval = new double[lclendArg.length];
            for (int i = 0; i < lclendArg.length; i++) {
                rval[i] = lclendArg[i];
            }
        }

        return rval;

    }

    protected static DestinationLocation extractEnd(CostArgs args) {
        Object endArg = args.getArgs().containsKey(POINT_TWO_TAG)
                ? args.getArgs().get(POINT_TWO_TAG)
                : args.getSubject();

        return new DestinationLocation("End", FILLER_TYPE, normalizeLocation(endArg));
    }

    protected static long extractStartTime(CostArgs args) {
        Object startTimeArg = args.getArgs().get(START_TIME_TAG);
        if (startTimeArg instanceof Long) return (Long) startTimeArg;
        if (startTimeArg instanceof Integer) return (Integer) startTimeArg;
        if (startTimeArg instanceof TimePoint) return ((TimePoint) startTimeArg).getUnixTime();
        if (startTimeArg instanceof Calendar) return ((Calendar) startTimeArg).getTimeInMillis();
        return -1;
    }

}
