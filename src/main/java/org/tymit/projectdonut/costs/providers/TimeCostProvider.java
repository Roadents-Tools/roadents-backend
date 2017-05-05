package org.tymit.projectdonut.costs.providers;

import com.google.common.collect.Lists;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.model.LocationPoint;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Created by ilan on 5/2/17.
 */
public abstract class TimeCostProvider implements CostProvider {

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

    private OkHttpClient client = new OkHttpClient();
    private String apiKey;
    private int calls = 0;


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

        String url = buildCallUrl(Lists.newArrayList(start), Lists.newArrayList(end), startTime);
        JSONObject obj = runCall(url);
        if (obj == null) return null;
        TimeDelta[][] timeMatrix = extractTimeMatrix(obj);
        if (timeMatrix == null
                || timeMatrix.length < 1
                || timeMatrix[0].length < 1
                || timeMatrix[0][0].getDeltaLong() < 0) {
            return null;
        }
        return timeMatrix[0][0];
    }

    protected abstract TimeDelta[][] extractTimeMatrix(JSONObject json);

    @Override
    public boolean isUp() {
        return calls <= getMaxCalls();
    }

    protected abstract int getMaxCalls();

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

    private static long extractStartTime(CostArgs args) {
        Object startTimeArg = args.getArgs().get(START_TIME_TAG);
        if (startTimeArg instanceof Long) return (Long) startTimeArg;
        if (startTimeArg instanceof Integer) return (Integer) startTimeArg;
        if (startTimeArg instanceof TimePoint) return ((TimePoint) startTimeArg).getUnixTime();
        if (startTimeArg instanceof Calendar) return ((Calendar) startTimeArg).getTimeInMillis();
        return -1;
    }

    /**
     * Call running methods
     **/
    private String buildCallUrl(List<double[]> srcs, List<double[]> dests, long startTime) {
        String srcStr = srcs.parallelStream()
                .map(latlng -> String.format(getLocationFormat(), latlng[0], latlng[1]))
                .collect(() -> new StringJoiner(getLocationSeparator()), StringJoiner::add, StringJoiner::merge)
                .toString();

        String destStr = dests.parallelStream()
                .map(latlng -> String.format(getLocationFormat(), latlng[0], latlng[1]))
                .collect(() -> new StringJoiner(getLocationSeparator()), StringJoiner::add, StringJoiner::merge)
                .toString();

        return String.format(getCallUrlFormat(), srcStr, destStr, startTime, apiKey);
    }

    protected abstract String getCallUrlFormat();

    protected abstract String getLocationFormat();

    protected abstract String getLocationSeparator();

    private JSONObject runCall(String url) {
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

    private static Predicate<TimeDelta> extractComparison(CostArgs args) {

        long border = extractCompareBorder(args);
        Object compTagArg = args.getArgs().get(COMPARISON_TAG);

        if (compTagArg instanceof Character) {
            switch ((Character) compTagArg) {
                case '<':
                    return dt -> dt == null || dt.getDeltaLong() < border;
                case '>':
                    return dt -> dt == null || dt.getDeltaLong() > border;
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

    private static long extractCompareBorder(CostArgs args) {
        Object rvalArg = args.getArgs().get(COMPARE_VALUE_TAG);
        if (rvalArg instanceof Long) return (Long) rvalArg;
        else if (rvalArg instanceof TimeDelta) return ((TimeDelta) rvalArg).getDeltaLong();
        return 0;
    }


}
