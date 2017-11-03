package com.reroute.backend.stations.postgresql;

/**
 * Contains constants for the Postgresql Database schema.
 * Created by ilan on 3/7/17.
 */
public final class PostgresqlContract {

    private PostgresqlContract() {
    }

    public static final class ChainTable {

        public static final String TABLE_NAME = "transchain";
        public static final String NAME_KEY = "name";
        public static final String ID_KEY = "id";

        private ChainTable() {
        }
    }

    public static final class StationTable {

        public static final String TABLE_NAME = "transstation";
        public static final String ID_KEY = "id";
        public static final String NAME_KEY = "name";
        public static final String LATLNG_KEY = "latlng";

        private StationTable() {
        }
    }


    public static final class ScheduleTable {

        public static final String TABLE_NAME = "schedule";
        public static final String ID_KEY = "id";
        public static final String FUZZ_KEY = "fuzz";
        public static final String CHAIN_ID_KEY = "chainid";
        public static final String STATION_ID_KEY = "stationid";
        public static final String PACKED_VALID_KEY = "validdays";
        public static final String PACKED_TIME_KEY = "packedtime";

        private ScheduleTable() {
        }
    }

    public static final class ChainsForStationsView {
        public static final String TABLE_NAME = "chains_for_stations";
        public static final String STATION_ID_KEY = "id";
        public static final String SCHEDULE_ID_KEY = "scid";
        public static final String PACKED_VALID_KEY = "validdays";
        public static final String PACKED_TIME_KEY = "packedtime";
        public static final String FUZZ_KEY = "fuzz";
        public static final String CHAIN_ID_KEY = "cid";
        public static final String CHAIN_NAME_KEY = "name";
    }

    public static final class StationsForChainsView {
        public static final String TABLE_NAME = "stations_for_chains";
        public static final String CHAIN_ID_KEY = "id";
        public static final String SCHEDULE_ID_KEY = "scid";
        public static final String PACKED_VALID_KEY = "validdays";
        public static final String PACKED_TIME_KEY = "packedtime";
        public static final String FUZZ_KEY = "fuzz";
        public static final String STATION_ID_KEY = "sid";
        public static final String STATION_NAME_KEY = "name";
        public static final String STATION_LAT_KEY = "latitude";
        public static final String STATION_LNG_KEY = "longitude";
    }
}
