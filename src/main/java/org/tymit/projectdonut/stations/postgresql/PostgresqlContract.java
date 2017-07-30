package org.tymit.projectdonut.stations.postgresql;

/**
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

    public static final class CostTable {

        public static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
        public static final String COST_ID_KEY = "id";
        public static final String COST_STATION_KEY = "stationId";
        public static final String COST_CHAIN_KEY = "chainId";

        private CostTable() {
        }

    }

    public static final class ScheduleTable {

        public static final String TABLE_NAME = "schedule";
        public static final String ID_KEY = "id";
        public static final String FUZZ_KEY = "fuzz";
        public static final String TIME_KEY = "schedpoint";
        public static final String COST_ID_KEY = "costid";
        public static final String CHAIN_ID_KEY = "chainid";
        public static final String STATION_ID_KEY = "stationid";
        public static final String PACKED_VALID_KEY = "validdays";

        private ScheduleTable() {
        }
    }

    public static final class RangeTable {

        public static final String TABLE_NAME = "ranges";
        public static final String ID_KEY = "id";
        public static final String FUZZ_KEY = "maxfuzz";
        public static final String TIME_KEY = "startime";
        public static final String LAT_KEY = "latlng";
        public static final String BOX_KEY = "distance";

        private RangeTable() {
        }

    }

    public static final String JOIN_VIEW_NAME = "main_join_view";
}
