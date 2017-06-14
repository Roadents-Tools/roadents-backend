package org.tymit.projectdonut.stations.dynamodb;

/**
 * Created by ilan on 6/9/17.
 */
public final class DynamoDbContract {
    private DynamoDbContract() {
    }

    public static final class StationTable {
        public static String TABLE_NAME = "Stations";
        public static String STATION_NAME = "name";
        public static String GEOHASH = "hash";
        public static int GEOHASH_BITS = 13;  //About 50 mile boxes
        public static String LONG_HASH = "range";
        public static int LONGHASH_BITS = 64;
        public static String LATITUDE = "latitude";
        public static String LONGITUDE = "longitude";
        public static String CHAIN_LIST_MAP = "chains";

        private StationTable() {
        }

    }

    public static final class ChainTable {
        public static String TABLE_NAME = "Chains";
        public static String CHAIN_NAME = "name";
        public static String STATION_LIST_MAP = "stations";
        public static String STATION_NAME = "name";
        public static String LATITUDE = "latitude";
        public static String LONGITUDE = "longitude";

        private ChainTable() {
        }
    }

    public static final class ScheduleSchema {

        public static String SCHEDULE_LIST = "schedule";
        public static String SCHEDULE_HOUR = "hour";
        public static String SCHEDULE_MINUTE = "minute";
        public static String SCHEDULE_SECOND = "second";
        public static String SCHEDULE_FUZZ = "fuzz";
        public static String VALID_DAYS = "valid";
    }
}
