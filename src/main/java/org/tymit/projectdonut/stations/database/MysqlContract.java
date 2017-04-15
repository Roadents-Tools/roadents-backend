package org.tymit.projectdonut.stations.database;

/**
 * Created by ilan on 3/7/17.
 */
public final class MysqlContract {

    public static final String CHAIN_TABLE_NAME = "transchain";
    public static final String CHAIN_NAME_KEY = CHAIN_TABLE_NAME + ".name";
    public static final String CHAIN_ID_KEY = CHAIN_TABLE_NAME + ".id";

    public static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
    public static final String COST_ID_KEY = STATION_CHAIN_COST_TABLE_NAME + ".id";
    public static final String COST_STATION_KEY = STATION_CHAIN_COST_TABLE_NAME + ".stationId";
    public static final String COST_CHAIN_KEY = STATION_CHAIN_COST_TABLE_NAME + ".chainId";

    public static final String SCHEDULE_TABLE_NAME = "schedule";
    public static final String SCHEDULE_ID_KEY = SCHEDULE_TABLE_NAME + ".id";
    public static final String SCHEDULE_FUZZ_KEY = SCHEDULE_TABLE_NAME + ".fuzz";
    public static final String SCHEDULE_SECOND_KEY = SCHEDULE_TABLE_NAME + ".second";
    public static final String SCHEDULE_MINUTE_KEY = SCHEDULE_TABLE_NAME + ".minute";
    public static final String SCHEDULE_HOUR_KEY = SCHEDULE_TABLE_NAME + ".hour";
    public static final String SCHEDULE_STATION_KEY = SCHEDULE_TABLE_NAME + ".stationid";
    public static final String SCHEDULE_CHAIN_KEY = SCHEDULE_TABLE_NAME + ".chainid";
    public static final String SCHEDULE_SUNDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".sunday";
    public static final String SCHEDULE_SATURDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".saturday";
    public static final String SCHEDULE_FRIDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".friday";
    public static final String SCHEDULE_THURSDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".thursday";
    public static final String SCHEDULE_WEDNESDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".wednesday";
    public static final String SCHEDULE_TUESDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".tuesday";
    public static final String SCHEDULE_MONDAY_VALID_KEY = SCHEDULE_TABLE_NAME + ".monday";

    public static final String RANGE_TABLE_NAME = "ranges";
    public static final String RANGE_ID_KEY = RANGE_TABLE_NAME + ".id";
    public static final String RANGE_FUZZ_KEY = RANGE_TABLE_NAME + ".fuzz";
    public static final String RANGE_TIME_KEY = RANGE_TABLE_NAME + ".unixtime";
    public static final String RANGE_LONG_KEY = RANGE_TABLE_NAME + ".longitude";
    public static final String RANGE_LAT_KEY = RANGE_TABLE_NAME + ".latitude";
    public static final String RANGE_BOX_KEY = RANGE_TABLE_NAME + ".range";

    public static final String STATION_TABLE_NAME = "transstation";
    public static final String STATION_ID_KEY = STATION_TABLE_NAME + ".id";
    public static final String STATION_NAME_KEY = STATION_TABLE_NAME + ".name";
    public static final String STATION_LAT_KEY = STATION_TABLE_NAME + ".latitude";
    public static final String STATION_LONG_KEY = STATION_TABLE_NAME + ".longitude";

    private MysqlContract() {
    }
}
