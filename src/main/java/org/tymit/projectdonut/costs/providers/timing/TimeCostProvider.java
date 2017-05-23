package org.tymit.projectdonut.costs.providers.timing;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.costs.providers.CostProvider;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.IOException;
import java.util.Calendar;
import java.util.function.Predicate;

/**
 * Created by ilan on 5/2/17.
 */
public abstract class TimeCostProvider implements CostProvider {

    /**
     Subject: a LocationPoint or double[] representing the point to start at in the case a p2 is passed,
     or a LocationPoint or double[] representing the point to end at in the case a p1 is passed.
     This ability is for the sake of easily being able to handle split up bulk request args.
     Note that if both p1 and p2 are set, then the subject is ignored.
    Args:
     - starttime = the TimePoint or unixtime in milliseconds since the unix epoch to start at
     - p2 = the LocationPoint or double[] to travel to
     - p1 = the LocationPoint or double[] to travel from
     - compareTo = the TimeDelta or millisecond long to compare the time between A and B to
     - comparison = the comparison to make in an isWithinCost call,
       either >,<,>=,<=,==, or !=.
     */

    public static final String TAG = "time";
    public static final String START_TIME_TAG = "starttime";
    public static final String COMPARISON_TAG = "comparison";
    public static final String COMPARE_VALUE_TAG = "compareto";
    public static final String POINT_TWO_TAG = "p2";
    public static final String POINT_ONE_TAG = "p1";
    protected String apiKey;
    protected int calls = 0;
    private OkHttpClient client = new OkHttpClient();


    public TimeCostProvider(String apiKey) {
        this.apiKey = apiKey;
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
        double[] start = extractStart(arg);
        double[] end = extractEnd(arg);
        long startTime = extractStartTime(arg);

        String url = buildCallUrl(start, end, startTime);
        JSONObject obj = runCall(url);
        if (obj == null) return null;
        TimeDelta timeMatrix = extractTimeDelta(obj);
        if (timeMatrix == null || timeMatrix.getDeltaLong() < 0) {
            return null;
        }
        return timeMatrix;
    }

    protected abstract String buildCallUrl(double[] start, double[] end, long startTime);

    protected abstract TimeDelta extractTimeDelta(JSONObject obj);

    @Override
    public boolean isUp() {
        return calls <= getMaxCalls();
    }

    protected abstract int getMaxCalls();

    protected static double[] extractStart(CostArgs args) {
        Object startArg = args.getArgs().containsKey(POINT_ONE_TAG)
                ? args.getArgs().get(POINT_ONE_TAG)
                : args.getSubject();

        return normalizeLocation(startArg);
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

    protected static double[] extractEnd(CostArgs args) {
        Object endArg = args.getArgs().containsKey(POINT_TWO_TAG)
                ? args.getArgs().get(POINT_TWO_TAG)
                : args.getSubject();

        return normalizeLocation(endArg);
    }

    protected static long extractStartTime(CostArgs args) {
        Object startTimeArg = args.getArgs().get(START_TIME_TAG);
        if (startTimeArg instanceof Long) return (Long) startTimeArg;
        if (startTimeArg instanceof Integer) return (Integer) startTimeArg;
        if (startTimeArg instanceof TimePoint) return ((TimePoint) startTimeArg).getUnixTime();
        if (startTimeArg instanceof Calendar) return ((Calendar) startTimeArg).getTimeInMillis();
        return -1;
    }

    protected JSONObject runCall(String url) {
        Request req = new Request.Builder().url(url).build();
        try {
            calls++;
            Response res = client.newCall(req).execute();
            return new JSONObject(res.body().string());
        } catch (IOException e) {
            LoggingUtils.logError(e);
            return null;
        }
    }

    protected static Predicate<TimeDelta> extractComparison(CostArgs args) {

        long border = extractCompareBorder(args);
        Object compTagArg = args.getArgs().get(COMPARISON_TAG);

        if (compTagArg instanceof Character) {
            switch ((Character) compTagArg) {
                case '<':
                    return dt -> dt == null || dt.getDeltaLong() < border;
                case '>':
                    return dt -> dt == null || dt.getDeltaLong() > border;
                case '=':
                    return dt -> dt == null || dt.getDeltaLong() == border;
            }
        }

        if (compTagArg instanceof String) {
            switch ((String) compTagArg) {
                case "<":
                    return dt -> dt == null || dt.getDeltaLong() < border;
                case "<=":
                    return dt -> dt == null || dt.getDeltaLong() <= border;
                case "=<": //Just in case
                    return dt -> dt == null || dt.getDeltaLong() <= border;

                case ">":
                    return dt -> dt == null || dt.getDeltaLong() > border;
                case ">=":
                    return dt -> dt == null || dt.getDeltaLong() >= border;
                case "=>": //Just in case
                    return dt -> dt == null || dt.getDeltaLong() >= border;

                case "==":
                    return dt -> dt == null || dt.getDeltaLong() == border;
                case "===": //For the Javascript devs
                    return dt -> dt == null || dt.getDeltaLong() == border;
                case "=":   //For the typos
                    return dt -> dt == null || dt.getDeltaLong() == border;

                case "!=":
                    return dt -> dt == null || dt.getDeltaLong() != border;
                case "!==":
                    return dt -> dt == null || dt.getDeltaLong() != border;
            }
        }

        return a -> true;
    }

    protected static long extractCompareBorder(CostArgs args) {
        Object rvalArg = args.getArgs().get(COMPARE_VALUE_TAG);
        if (rvalArg instanceof Long) return (Long) rvalArg;
        else if (rvalArg instanceof TimeDelta) return ((TimeDelta) rvalArg).getDeltaLong();
        return 0;
    }


}
