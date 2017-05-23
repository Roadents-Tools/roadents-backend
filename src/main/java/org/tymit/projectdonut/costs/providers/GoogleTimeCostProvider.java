package org.tymit.projectdonut.costs.providers;

import com.google.common.collect.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.costs.BulkCostArgs;
import org.tymit.projectdonut.costs.CostArgs;
import org.tymit.projectdonut.model.TimeDelta;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by ilan on 5/5/17.
 */
public class GoogleTimeCostProvider extends TimeCostProvider implements BulkCostProvider {

    public static final String[] API_KEYS = {
            "AIzaSyB0pBdXuC4VRte73qnVtE5pLmxNs3ju0Gg"
    };

    private static final String URL_FORMAT = "https://maps.googleapis.com/maps/api/distancematrix/json?%s";

    private static final String LOCATION_FORMAT = "%f,%f";
    private static final String LOCATION_SEPARATOR = "|";
    private static final String PARAM_SEPARATOR = "&";
    private static final String KEY_PREFIX = "key=";
    private static final String ORIGINS_PREFIX = "origins=";
    private static final String DESTINATIONS_PREFIX = "destinations=";
    private static final String MODE_PREFIX = "mode=";
    private static final String DEPARTURE_TIME_PREFIX = "departure_time=";
    private static final String TRAVEL_MODE_PRIMARY = "transit";
    private static final String TRAVEL_MODE_SECONDARY = "walking";


    public GoogleTimeCostProvider(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String buildCallUrl(double[] start, double[] end, long startTime) {
        return buildPrimaryCallUrl(Lists.newArrayList(start), Lists.newArrayList(end), startTime);
    }

    private String buildPrimaryCallUrl(List<double[]> start, List<double[]> end, long startTime) {
        StringBuilder rval = new StringBuilder();

        StringJoiner startJoiner = start.stream()
                .map(coord -> String.format(LOCATION_FORMAT, coord[0], coord[1]))
                .collect(() -> new StringJoiner(LOCATION_SEPARATOR), StringJoiner::add, StringJoiner::merge);
        rval.append(ORIGINS_PREFIX).append(startJoiner.toString()).append(PARAM_SEPARATOR);

        StringJoiner endJoiner = end.stream()
                .map(coord -> String.format(LOCATION_FORMAT, coord[0], coord[1]))
                .collect(() -> new StringJoiner(LOCATION_SEPARATOR), StringJoiner::add, StringJoiner::merge);
        rval.append(DESTINATIONS_PREFIX).append(endJoiner.toString()).append(PARAM_SEPARATOR);

        rval.append(MODE_PREFIX).append(TRAVEL_MODE_PRIMARY).append(PARAM_SEPARATOR);
        rval.append(DEPARTURE_TIME_PREFIX).append(startTime).append(PARAM_SEPARATOR);
        rval.append(KEY_PREFIX).append(apiKey).append(PARAM_SEPARATOR);

        return String.format(URL_FORMAT, rval.toString());
    }

    @Override
    protected TimeDelta extractTimeDelta(JSONObject obj) {
        return exctractTimeMatrix(obj)[0][0];
    }

    private TimeDelta[][] exctractTimeMatrix(JSONObject obj) {
        JSONArray rows = obj.getJSONArray("rows");
        int numRows = rows.length();
        int numCols = rows.getJSONObject(0).getJSONArray("elements").length();
        TimeDelta[][] rval = new TimeDelta[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            JSONArray curRow = rows.getJSONObject(i).getJSONArray("elements");
            for (int j = 0; j < numCols; j++) {
                JSONObject resultObj = curRow.getJSONObject(j);
                if (resultObj.get("status").equals("ZERO_RESULTS")) {
                    rval[i][j] = new TimeDelta(-1);
                } else {
                    long deltaVal = resultObj.getJSONObject("duration").getLong("value");
                    rval[i][j] = new TimeDelta(deltaVal);
                }
            }
        }

        return rval;
    }

    @Override
    protected int getMaxCalls() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public Map<Object, Boolean> isWithinCosts(BulkCostArgs args) {

        Predicate<TimeDelta> comparison = extractComparison(args.splitSingular().values().iterator().next());
        Map<Object, Object> values = getCostValue(args);

        Map<Object, Boolean> rval = new ConcurrentHashMap<>();
        for (Object key : values.keySet()) {
            rval.put(key, comparison.test((TimeDelta) values.get(key)));
        }
        return rval;
    }

    @Override
    public Map<Object, Object> getCostValue(BulkCostArgs args) {

        /* Extract, parse, and verify arguments */
        List<CostArgs> singleArgs = Lists.newArrayList(args.splitSingular().values());

        List<double[]> starts = singleArgs.stream()
                .map(TimeCostProvider::extractStart)
                .distinct()
                .collect(Collectors.toList());
        if (starts == null || starts.isEmpty()) return new ConcurrentHashMap<>();
        int startSize = starts.size();

        List<double[]> ends = singleArgs.stream()
                .map(TimeCostProvider::extractEnd)
                .distinct()
                .collect(Collectors.toList());
        if (ends == null || ends.isEmpty()) return new ConcurrentHashMap<>();
        int endSize = ends.size();

        if (startSize > 1 && endSize > 1) return new ConcurrentHashMap<>(); //Do not yet support many-to-many

        long startTime = extractStartTime(singleArgs.iterator().next());
        if (startTime < 0) return new ConcurrentHashMap<>();

        /* Run external API call */
        String url = buildPrimaryCallUrl(starts, ends, startTime);
        JSONObject obj = runCall(url);
        if (obj == null) return new ConcurrentHashMap<>();
        TimeDelta[][] timeMatrix = exctractTimeMatrix(obj);

        /* Format and parse results */
        Map<Object, Object> rval = new ConcurrentHashMap<>();

        for (int i = 0; i < startSize; i++) {
            for (int j = 0; j < endSize; j++) {
                int keyIndex = (i > j) ? i : j;
                rval.put(singleArgs.get(keyIndex).getSubject(), timeMatrix[i][j]);
            }
        }

        /* Google can't yet do transit and walking at the same time. */
        /* Check for uncalculated entries */
        List<Object> unbusedKeys = rval.entrySet().stream()
                .filter(entry -> ((TimeDelta) entry.getValue()).getDeltaLong() == -1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (unbusedKeys.isEmpty()) return rval;

        /* Build the call arguments */
        List<double[]> normalizedUnbusedKeys = unbusedKeys.stream()
                .map(TimeCostProvider::normalizeLocation)
                .collect(Collectors.toList());

        List<double[]> unbusedStarts = startSize == 1 ? starts : normalizedUnbusedKeys;
        List<double[]> unbusedEnds = endSize == 1 ? ends : normalizedUnbusedKeys;

        /* Run the call */
        JSONObject unbusedObj = runCall(buildSecondaryCallUrl(unbusedStarts, unbusedEnds, startTime));
        if (unbusedObj == null) return rval;
        TimeDelta[][] unbusedTimeMatrix = exctractTimeMatrix(unbusedObj);

        /* Replace invalid results */
        for (int i = 0; i < unbusedTimeMatrix.length; i++) {
            for (int j = 0; j < unbusedTimeMatrix[0].length; j++) {
                int keyIndex = (i > j) ? i : j;
                rval.replace(unbusedKeys.get(keyIndex), unbusedTimeMatrix[i][j]);
            }
        }

        return rval;
    }

    private String buildSecondaryCallUrl(List<double[]> start, List<double[]> end, long startTime) {
        StringBuilder rval = new StringBuilder();

        StringJoiner startJoiner = start.stream()
                .map(coord -> String.format(LOCATION_FORMAT, coord[0], coord[1]))
                .collect(() -> new StringJoiner(LOCATION_SEPARATOR), StringJoiner::add, StringJoiner::merge);
        rval.append(ORIGINS_PREFIX).append(startJoiner.toString()).append(PARAM_SEPARATOR);

        StringJoiner endJoiner = end.stream()
                .map(coord -> String.format(LOCATION_FORMAT, coord[0], coord[1]))
                .collect(() -> new StringJoiner(LOCATION_SEPARATOR), StringJoiner::add, StringJoiner::merge);
        rval.append(DESTINATIONS_PREFIX).append(endJoiner.toString()).append(PARAM_SEPARATOR);

        rval.append(MODE_PREFIX).append(TRAVEL_MODE_SECONDARY).append(PARAM_SEPARATOR);
        rval.append(DEPARTURE_TIME_PREFIX).append(startTime).append(PARAM_SEPARATOR);
        rval.append(KEY_PREFIX).append(apiKey).append(PARAM_SEPARATOR);

        return String.format(URL_FORMAT, rval.toString());
    }

}
