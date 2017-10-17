package com.reroute.backend.stations.redis;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.utils.TimeUtils;

import java.util.List;
import java.util.function.Predicate;

public final class RedisUtils {

    public static final String WORLD_LIST_NAME = "cachedworlds";

    public static final String STATION_PREFIX_NAME = "station";
    public static final String LATLNG_INDEX = "station_latlng_idx";

    public static final String CHAIN_PREFIX_NAME = "chain";

    public static final String SCHEDULE_PREFIX_NAME = "schedule";
    public static final String SCHEDULE_CHAIN_INDEX_PREFIX = "schedule_chain_idx";
    public static final String SCHEDULE_STATION_INDEX_PREFIX = "schedule_station_idx";

    public static final String DATA_SPLITER = ";:;";
    public static final String KEY_SPLITER = ":;:";

    private RedisUtils() {
    }

    public static <T, Q> Predicate<Integer> indexNonNull(T[] keys, List<Q> toVerify) {
        return index -> toVerify.get(index) != null;
    }

    public static String[] packStation(TransStation station) {
        String keyString = STATION_PREFIX_NAME + KEY_SPLITER +
                station.getID().getDatabaseName() + KEY_SPLITER +
                station.getID().getId();

        String valueString = station.getName() + DATA_SPLITER +
                station.getCoordinates()[0] + DATA_SPLITER +
                station.getCoordinates()[1];

        return new String[] { keyString, valueString };
    }

    public static TransStation unpackStation(String idKey, String stationString) {
        String[] splitId = idKey.split(KEY_SPLITER);
        DatabaseID id = new DatabaseID(splitId[1], splitId[2]);

        String[] splitData = stationString.split(DATA_SPLITER);
        String name = splitData[0];
        double lat = Double.parseDouble(splitData[1]);
        double lng = Double.parseDouble(splitData[2]);

        return new TransStation(name, new double[] { lat, lng }, id);
    }

    public static String[] packChain(TransChain chain) {
        String keyString = CHAIN_PREFIX_NAME + KEY_SPLITER +
                chain.getID().getDatabaseName() + KEY_SPLITER +
                chain.getID().getId();

        String valueString = chain.getName();
        return new String[] { keyString, valueString };
    }

    public static TransChain unpackChain(String idKey, String chainString) {
        String[] splitId = idKey.split(KEY_SPLITER);
        DatabaseID id = new DatabaseID(splitId[1], splitId[2]);

        String[] splitData = chainString.split(DATA_SPLITER);
        String name = splitData[0];

        return new TransChain(name, id);
    }

    public static String[] packSchedule(SchedulePoint point) {
        String keyString = SCHEDULE_PREFIX_NAME + KEY_SPLITER +
                point.getID().getDatabaseName() + KEY_SPLITER +
                point.getID().getId();

        String valueString = TimeUtils.packSchedulePoint(point) + DATA_SPLITER +
                point.getFuzz() + DATA_SPLITER +
                TimeUtils.boolsToBitStr(point.getValidDays());

        return new String[] { keyString, valueString };
    }

    public static SchedulePoint unpackSchedule(String idKey, String scheduleString) {
        String[] splitId = idKey.split(KEY_SPLITER);
        DatabaseID id = new DatabaseID(splitId[1], splitId[2]);

        String[] splitData = scheduleString.split(DATA_SPLITER);
        int packedTime = Integer.parseInt(splitData[0]);
        long fuzz = Long.parseLong(splitData[1]);
        String packedValid = splitData[2];

        return TimeUtils.unpackPoint(packedTime, TimeUtils.bitStrToBools(packedValid), fuzz, id);
    }
}
