package org.tymit.projectdonut.stations.caches;

/**
 * Created by ilan on 3/7/17.
 */
public final class PostgresqlContract {

    public static final String CHAIN_TABLE_NAME = "transchain";
    public static final String CHAIN_NAME_KEY = "name";
    public static final String CHAIN_ID_KEY = "id";

    public static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
    public static final String COST_ID_KEY = "id";
    public static final String COST_STATION_KEY = "stationId";
    public static final String COST_CHAIN_KEY = "chainId";

    public static final String SCHEDULE_TABLE_NAME = "schedule";
    public static final String SCHEDULE_ID_KEY = "id";
    public static final String SCHEDULE_FUZZ_KEY = "fuzz";
    public static final String SCHEDULE_TIME_KEY = "schedpoint";
    public static final String SCHEDULE_COST_ID_KEY = "costid";
    public static final String SCHEDULE_SUNDAY_VALID_KEY = "sunday";
    public static final String SCHEDULE_SATURDAY_VALID_KEY = "saturday";
    public static final String SCHEDULE_FRIDAY_VALID_KEY = "friday";
    public static final String SCHEDULE_THURSDAY_VALID_KEY = "thursday";
    public static final String SCHEDULE_WEDNESDAY_VALID_KEY = "wednesday";
    public static final String SCHEDULE_TUESDAY_VALID_KEY = "tuesday";
    public static final String SCHEDULE_MONDAY_VALID_KEY = "monday";

    public static final String RANGE_TABLE_NAME = "ranges";
    public static final String RANGE_ID_KEY = "id";
    public static final String RANGE_FUZZ_KEY = "maxfuzz";
    public static final String RANGE_TIME_KEY = "startime";
    public static final String RANGE_LAT_KEY = "latlng";
    public static final String RANGE_BOX_KEY = "distance";

    public static final String STATION_TABLE_NAME = "transstation";
    public static final String STATION_ID_KEY = "id";
    public static final String STATION_NAME_KEY = "name";
    public static final String STATION_LATLNG_KEY = "latlng";

    private PostgresqlContract() {
    }
}
