package org.tymit.projectdonut.stations.dynamodb;

import ch.hsr.geohash.GeoHash;
import com.amazonaws.services.dynamodbv2.document.BatchWriteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.TableWriteItems;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ilan on 6/5/17.
 */
public final class DynamoDbSupport {

    private static final int BATCH_SIZE = 25;
    public static Collector<JSONObject, JSONArray, JSONArray> collectToJsonArray = new Collector<JSONObject, JSONArray, JSONArray>() {

        @Override
        public Supplier<JSONArray> supplier() {
            return JSONArray::new;
        }

        @Override
        public BiConsumer<JSONArray, JSONObject> accumulator() {
            return JSONArray::put;
        }

        @Override
        public BinaryOperator<JSONArray> combiner() {
            return (jsonArray, jsonArray2) -> {
                IntStream.range(0, jsonArray2.length()).boxed().map(jsonArray2::getJSONObject).forEach(jsonArray::put);
                return jsonArray;
            };
        }

        @Override
        public Function<JSONArray, JSONArray> finisher() {
            return a -> a;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.newHashSet(Characteristics.IDENTITY_FINISH);
        }
    };
    /**
     * Utility Reading Methods
     **/

    public static Function<JSONObject, SchedulePoint> readSchedulePoint = rawPt -> {
        boolean[] validDays = unpackScheduleValids((char) rawPt.getInt(DynamoDbContract.ScheduleSchema.VALID_DAYS));
        int hour = rawPt.getInt(DynamoDbContract.ScheduleSchema.SCHEDULE_HOUR);
        int minute = rawPt.getInt(DynamoDbContract.ScheduleSchema.SCHEDULE_MINUTE);
        int second = rawPt.getInt(DynamoDbContract.ScheduleSchema.SCHEDULE_SECOND);
        long fuzz = rawPt.getLong(DynamoDbContract.ScheduleSchema.SCHEDULE_FUZZ);

        return new SchedulePoint(hour, minute, second, validDays, fuzz);
    };
    public static Function<SchedulePoint, JSONObject> convertSchedulePoint = pt -> new JSONObject()
            .put(DynamoDbContract.ScheduleSchema.SCHEDULE_HOUR, pt.getHour())
            .put(DynamoDbContract.ScheduleSchema.SCHEDULE_MINUTE, pt.getMinute())
            .put(DynamoDbContract.ScheduleSchema.SCHEDULE_SECOND, pt.getSecond())
            .put(DynamoDbContract.ScheduleSchema.SCHEDULE_FUZZ, pt.getFuzz())
            .put(DynamoDbContract.ScheduleSchema.VALID_DAYS, packScheduleValids(pt.getValidDays()));
    public static Function<Collection<? extends TransStation>, Item> convertStationS = stats -> {

        if (stats == null || stats.isEmpty()) return null;

        TransStation base = stats.stream().findAny().get();

        JSONObject schedule = new JSONObject();
        for (TransStation stat : stats) {
            JSONArray scheduleItems = stat.getSchedule().stream()
                    .map(convertSchedulePoint)
                    .collect(collectToJsonArray);
            schedule.put(stat.getChain().getName(), scheduleItems);
        }

        double lat = base.getCoordinates()[0];
        double lng = base.getCoordinates()[1];

        GeoHash shortHash = GeoHash.withBitPrecision(lat, lng, DynamoDbContract.StationTable.GEOHASH_BITS);
        GeoHash longHash = GeoHash.withBitPrecision(lat, lng, DynamoDbContract.StationTable.LONGHASH_BITS);

        return new Item()
                .withPrimaryKey(
                        DynamoDbContract.StationTable.GEOHASH, shortHash.ord(),
                        DynamoDbContract.StationTable.LONG_HASH, longHash.toBinaryString()
                )
                .withDouble(
                        DynamoDbContract.StationTable.LATITUDE, lat
                )
                .withDouble(
                        DynamoDbContract.StationTable.LONGITUDE, lng
                )
                .withString(DynamoDbContract.StationTable.STATION_NAME, base.getName())
                .withJSON(DynamoDbContract.StationTable.CHAIN_LIST_MAP, schedule.toString());
    };
    public static Function<TransStation, JSONObject> convertStationC = stat -> {
        JSONObject rval = new JSONObject();
        rval.put(DynamoDbContract.ChainTable.LATITUDE, stat.getCoordinates()[0]);
        rval.put(DynamoDbContract.ChainTable.LONGITUDE, stat.getCoordinates()[1]);
        rval.put(DynamoDbContract.ChainTable.STATION_NAME, stat.getName());

        JSONArray scheduleList = stat.getSchedule().stream()
                .map(convertSchedulePoint)
                .collect(collectToJsonArray);
        rval.put(DynamoDbContract.ScheduleSchema.SCHEDULE_LIST, scheduleList);
        return rval;
    };
    public static Function<TransChain, Item> convertChainC = chain -> {
        JSONArray stations = chain.getStations().stream()
                .map(convertStationC)
                .collect(collectToJsonArray);

        return new Item()
                .withPrimaryKey(DynamoDbContract.ChainTable.CHAIN_NAME, chain.getName())
                .withJSON(DynamoDbContract.ChainTable.STATION_LIST_MAP, stations.toString());
    };

    private DynamoDbSupport() {
    }

    /**
     * General Helper Methods
     **/
    public static char packScheduleValids(boolean[] schedule) {
        return (char) IntStream.range(0, schedule.length)
                .filter(i -> schedule[i])
                .map(i -> 1 << i)
                .sum();
    }

    public static boolean[] unpackScheduleValids(char packed) {
        boolean[] rval = new boolean[7];
        for (int i = 0; i < rval.length; i++) {
            rval[i] = (packed & 1 << i) != 0;
        }
        return rval;
    }

    /**
     * Station Reading Methods
     **/
    public static List<TransStation> readItemsS(List<Item> items) {
        return items.stream()
                .map(DynamoDbSupport::readItemS)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public static List<TransStation> readItemS(Item item) {
        double latitude = item.getDouble(DynamoDbContract.StationTable.LATITUDE);
        double longitude = item.getDouble(DynamoDbContract.StationTable.LONGITUDE);
        String name = item.getString(DynamoDbContract.StationTable.STATION_NAME);
        TransStation base = new TransStation(name, new double[] { latitude, longitude });

        Map<String, TransChain> chainMap = new HashMap<>();
        JSONObject scheduleMap = new JSONObject(item.getJSON(DynamoDbContract.StationTable.CHAIN_LIST_MAP));

        return Stream.of(scheduleMap.keySet())
                .flatMap(Collection::stream)
                .map(String.class::cast)
                .peek(chainName -> chainMap.putIfAbsent(chainName, new TransChain(chainName)))
                .map(chainName -> {
                    JSONArray schedarr = scheduleMap.getJSONArray(chainName);
                    int schedlen = schedarr.length();
                    List<SchedulePoint> schedule = IntStream.range(0, schedlen)
                            .boxed()
                            .map(schedarr::getJSONObject)
                            .map(readSchedulePoint)
                            .collect(Collectors.toList());
                    return base.withSchedule(chainMap.get(chainName), schedule);
                })
                .collect(Collectors.toList());
    }

    /**
     * Chain Reading Methods
     **/

    public static List<TransStation> readItemC(Item item) {
        return readItemsC(Lists.newArrayList(item)).values().stream().findAny().get();
    }

    public static Map<TransChain, List<TransStation>> readItemsC(List<Item> items) {
        Map<TransChain, List<TransStation>> rval = new HashMap<>();

        for (Item item : items) {
            TransChain chain = new TransChain(item.getString(DynamoDbContract.ChainTable.CHAIN_NAME));
            List<TransStation> values = item.<JSONObject>getList(DynamoDbContract.ChainTable.STATION_LIST_MAP)
                    .stream()
                    .map(readStationC(chain))
                    .collect(Collectors.toList());
            rval.put(chain, values);
        }

        return rval;
    }

    public static Function<JSONObject, TransStation> readStationC(TransChain chain) {
        return statMap -> {
            JSONArray rawSchedule = (JSONArray) statMap.get(DynamoDbContract.ScheduleSchema.SCHEDULE_LIST);
            int arrLen = rawSchedule.length();
            List<SchedulePoint> schedule = IntStream.range(0, arrLen)
                    .boxed()
                    .map(rawSchedule::getJSONObject)
                    .map(readSchedulePoint)
                    .collect(Collectors.toList());

            String name = statMap.getString(DynamoDbContract.ChainTable.STATION_NAME);
            double lat = statMap.getDouble(DynamoDbContract.ChainTable.LATITUDE);
            double lng = statMap.getDouble(DynamoDbContract.ChainTable.LONGITUDE);

            return new TransStation(name, new double[] { lat, lng }, schedule, chain);
        };
    }

    /**
     * Utility Writing Methods
     **/

    public static boolean insertStations(Collection<? extends TransStation> stations, DynamoDB dynamoDB) {
        return insertItems(
                getStationInformation(stations).collect(Collectors.toList()),
                DynamoDbContract.StationTable.TABLE_NAME,
                dynamoDB
        ) && insertItems(
                getChainInformation(stations).collect(Collectors.toList()),
                DynamoDbContract.ChainTable.TABLE_NAME,
                dynamoDB
        );

    }

    public static boolean insertItems(List<Item> items, String table, DynamoDB dynamoDB) {

        Set<TableWriteItems> bulks = new HashSet<>();
        TableWriteItems tempreq = new TableWriteItems(table);
        for (Item item : items) {
            tempreq.addItemToPut(item);
            if (tempreq.getItemsToPut().size() >= BATCH_SIZE) {
                bulks.add(tempreq);
                tempreq = new TableWriteItems(table);
            }
        }
        bulks.add(tempreq);

        for (TableWriteItems req : bulks) {
            BatchWriteItemOutcome result = dynamoDB.batchWriteItem(req);

            Map<String, List<WriteRequest>> unprocessed = result.getUnprocessedItems();
            while (unprocessed != null && !unprocessed.isEmpty()) {
                Map<String, List<WriteRequest>> nextBatch = unprocessed.entrySet()
                        .stream()
                        .limit(24)
                        .collect(
                                HashMap::new,
                                (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                                HashMap::putAll
                        );
                unprocessed.putAll(dynamoDB.batchWriteItemUnprocessed(nextBatch).getUnprocessedItems());
            }
        }
        return true;
    }

    /**
     * Station Table Writing Methods
     **/

    public static Stream<Item> getStationInformation(Collection<? extends TransStation> stations) {
        Map<TransStation, Set<TransStation>> basestationToScheduled = new HashMap<>();
        for (TransStation station : stations) {
            basestationToScheduled.putIfAbsent(station.stripSchedule(), new HashSet<>());
            basestationToScheduled.get(station.stripSchedule()).add(station);
        }

        return basestationToScheduled.values().stream()
                .map(convertStationS);

    }

    /**
     * Chain Table Writing Methods
     **/

    public static Stream<Item> getChainInformation(Collection<? extends TransStation> stations) {
        Map<String, TransChain> allChains = new HashMap<>();
        for (TransStation station : stations) {
            allChains.putIfAbsent(station.getChain().getName(), station.getChain());
            allChains.get(station.getChain().getName()).addStation(station);
        }

        return allChains.values().stream()
                .map(convertChainC);
    }

    public static boolean insertStations(Map<TransChain, List<TransStation>> stations, DynamoDB dynamoDB) {
        return insertItems(
                getStationInformation(stations).collect(Collectors.toList()),
                DynamoDbContract.StationTable.TABLE_NAME,
                dynamoDB
        ) && insertItems(
                getChainInformation(stations).collect(Collectors.toList()),
                DynamoDbContract.ChainTable.TABLE_NAME,
                dynamoDB
        );
    }

    public static Stream<Item> getStationInformation(Map<TransChain, List<TransStation>> chains) {

        Map<TransStation, Set<TransStation>> basestationToScheduled = new HashMap<>();
        for (List<TransStation> stations : chains.values()) {
            for (TransStation station : stations) {
                basestationToScheduled.putIfAbsent(station.stripSchedule(), new HashSet<>());
                basestationToScheduled.get(station.stripSchedule()).add(station);
            }
        }

        return basestationToScheduled.values().stream()
                .map(convertStationS);

    }

    public static Stream<Item> getChainInformation(Map<TransChain, List<TransStation>> chains) {
        return chains.keySet().stream()
                .peek(chain -> chains.get(chain).forEach(chain::addStation))
                .map(convertChainC);
    }

}
