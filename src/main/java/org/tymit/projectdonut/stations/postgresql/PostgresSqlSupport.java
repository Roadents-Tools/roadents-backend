package org.tymit.projectdonut.stations.postgresql;

import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Created by ilan on 3/28/17.
 */
public class PostgresSqlSupport {

    private static final String[] SCHEDULE_DAY_FIELDS = new String[] {
            PostgresqlContract.SCHEDULE_SUNDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_MONDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_TUESDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_WEDNESDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_THURSDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_FRIDAY_VALID_KEY,
            PostgresqlContract.SCHEDULE_SATURDAY_VALID_KEY,
    };


    private static final String LAT_COL_NAME = "statlatman";
    private static final String LNG_COL_NAME = "statlngman";
    private static final String HOUR_COL_NAME = "hourman";
    private static final String MINUTE_COL_NAME = "minuteman";
    private static final String SECOND_COL_NAME = "secondman";
    private static final String STAT_NAM_KEY = "statnm";
    private static final String CHN_NAM_KEY = "chanm";


    private static final long MAX_POSTGRES_INTERVAL_MILLI = 1000L * 60L * 60L * 24L * 365L * 10L;
    private static final long BATCH_SIZE = 300;
    private static final long ERROR_MARGIN = 1; //meters


    /**
     * INFORMATION RETRIEVAL
     **/
    public static List<TransStation> getInformation(Connection con,
                                                    double[] center, double range,
                                                    TimePoint startTime, TimeDelta maxDelta,
                                                    TransChain chain
    ) throws SQLException {
        if (!isValidQuery(con, center, range, startTime, maxDelta, chain))
            return Collections.emptyList();
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(buildQuery(center, range, startTime, maxDelta, chain));
        Map<Integer, TransStation> idToStations = new HashMap<>();
        Map<Integer, TransChain> idToChain = new HashMap<>();
        Map<Integer, Map<Integer, List<SchedulePoint>>> chainStationSchedule = new HashMap<>();
        while (rs.isBeforeFirst()) rs.next();
        do {
            int statId = rs.getInt(PostgresqlContract.COST_STATION_KEY);
            if (!idToStations.containsKey(statId)) {
                TransStation nstat = new TransStation(rs.getString(STAT_NAM_KEY), new double[] { rs.getDouble(LAT_COL_NAME), rs.getDouble(LNG_COL_NAME) });
                idToStations.put(statId, nstat);
            }
            int chainId = rs.getInt(PostgresqlContract.COST_CHAIN_KEY);
            if (!idToChain.containsKey(chainId)) {
                TransChain nchn = new TransChain(rs.getString(CHN_NAM_KEY));
                idToChain.put(chainId, nchn);
            }
            chainStationSchedule.putIfAbsent(chainId, new HashMap<>());
            chainStationSchedule.get(chainId)
                    .putIfAbsent(statId, new ArrayList<>());
            SchedulePoint nsched = new SchedulePoint(
                    rs.getInt(HOUR_COL_NAME),
                    rs.getInt(MINUTE_COL_NAME),
                    rs.getInt(SECOND_COL_NAME),
                    new boolean[] {
                            rs.getBoolean(PostgresqlContract.SCHEDULE_SUNDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_MONDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_TUESDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_WEDNESDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_THURSDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_FRIDAY_VALID_KEY),
                            rs.getBoolean(PostgresqlContract.SCHEDULE_SATURDAY_VALID_KEY)
                    },
                    rs.getLong(PostgresqlContract.SCHEDULE_FUZZ_KEY)
            );
            chainStationSchedule.get(chainId).get(statId).add(nsched);
        } while (rs.next());
        Set<TransStation> rval = new HashSet<>();
        for (int chainid : chainStationSchedule.keySet()) {
            for (int statid : chainStationSchedule.get(chainid).keySet()) {
                rval.add(idToStations.get(statid)
                        .withSchedule(idToChain.get(chainid), chainStationSchedule
                                .get(chainid)
                                .get(statid)));
            }
        }
        return new ArrayList<>(rval);
    }

    private static String buildQuery(double[] center, double range,
                                     TimePoint startTime, TimeDelta maxDelta,
                                     TransChain chain
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("SELECT " +
                        "ST_X(%s.%s::geometry) AS %s, " +
                        "ST_Y(%s.%s::geometry) AS %s, " +
                        "EXTRACT(HOUR FROM %s.%s) AS %s, " +
                        "EXTRACT(MINUTE FROM %s.%s) AS %s, " +
                        "EXTRACT(SECOND FROM %s.%s) AS %s, " +
                        "%s.%s AS %s, " +
                        "%s.%s AS %s, *" +
                        "FROM %s, %s, %s, %s WHERE %s.%s=%s.%s AND %s.%s=%s.%s AND %s.%s = %s.%s ",

                //ALIASES
                PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_LATLNG_KEY, LAT_COL_NAME,
                PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_LATLNG_KEY, LNG_COL_NAME,
                PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_TIME_KEY, HOUR_COL_NAME,
                PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_TIME_KEY, MINUTE_COL_NAME,
                PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_TIME_KEY, SECOND_COL_NAME,
                PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_NAME_KEY, STAT_NAM_KEY,
                PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_NAME_KEY, CHN_NAM_KEY,

                //FROM
                PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.CHAIN_TABLE_NAME,

                //JOIN
                PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_COST_ID_KEY, PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_ID_KEY,
                PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_STATION_KEY, PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_ID_KEY,
                PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_CHAIN_KEY, PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_ID_KEY
        ));
        if (startTime != null && maxDelta != null) {
            builder.append(String.format("AND %s.%s=true AND (%s.%s, %s.%s * INTERVAL \'1 ms\') OVERLAPS (\'%d:%d:%d\', INTERVAL '%d milliseconds') ",
                    PostgresqlContract.SCHEDULE_TABLE_NAME, SCHEDULE_DAY_FIELDS[startTime
                            .getDayOfWeek()],
                    PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_TIME_KEY,
                    PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_FUZZ_KEY,
                    startTime.getHour(), startTime.getMinute(), startTime.getSecond(), maxDelta
                            .getDeltaLong()
            ));
        }
        if (center != null && range >= 0) {
            builder.append(String.format("AND ST_DWITHIN(%s.%s, ST_POINT(%f, %f)::geography, %f) ",
                    PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_LATLNG_KEY, center[0], center[1], LocationUtils
                            .milesToMeters(range)
            ));
        }
        if (chain != null) {
            builder.append(String.format("AND %s.%s=%s ",
                    PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_NAME_KEY, chain
                            .getName()
            ));
        }
        System.out.println(builder.toString());
        return builder.toString();
    }

    public static boolean isValidQuery(Connection con,
                                       double[] center, double range,
                                       TimePoint startTime, TimeDelta maxDelta,
                                       TransChain chain
    ) throws SQLException {
        if (center == null || startTime == null) return false;
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(String.format("SELECT %s FROM %s " +
                        "WHERE ST_DWithin(ST_POINT(%f, %f)::geography, %s, %f) " +
                        "AND %s <= to_timestamp(%d) " +
                        "AND %s + %s >= to_timestamp(%d)",
                PostgresqlContract.RANGE_ID_KEY, PostgresqlContract.RANGE_TABLE_NAME,
                center[0], center[1], PostgresqlContract.RANGE_LAT_KEY, LocationUtils
                        .milesToMeters(range),
                PostgresqlContract.RANGE_TIME_KEY, startTime.getUnixTime(),
                PostgresqlContract.RANGE_TIME_KEY, PostgresqlContract.RANGE_FUZZ_KEY,
                startTime.plus(maxDelta).getUnixTime()
        ));
        return rs.next();
    }

    /**
     * INFORMATION STORING
     **/
    public static boolean storeArea(Connection con,
                                    double[] center, double range,
                                    TimePoint startTime, TimeDelta maxDelta,
                                    Collection<? extends TransStation> stations
    ) throws SQLException {

        long deltlong = (MAX_POSTGRES_INTERVAL_MILLI > maxDelta.getDeltaLong()) ? maxDelta
                .getDeltaLong() : MAX_POSTGRES_INTERVAL_MILLI;
        Statement stm = con.createStatement();

        //Insert the chains into the database in a batch
        AtomicInteger ctprev = new AtomicInteger(0);
        stations.stream()
                .map(TransStation::getChain)
                .filter(Objects::nonNull)
                .distinct()
                .map(TransChain::getName)
                .map(chainName -> String.format("INSERT INTO %s(%s) VALUES ('%s') ON CONFLICT(%s) DO NOTHING;",
                        PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_NAME_KEY, chainName
                                .replace("'", "`"), PostgresqlContract.CHAIN_NAME_KEY)
                )
                .filter(Objects::nonNull)
                .forEach((LoggingUtils.WrappedConsumer<String>) (sql1) -> {
                    stm.addBatch(sql1);
                    if (ctprev.incrementAndGet() >= BATCH_SIZE) {
                        ctprev.getAndSet(0);
                        stm.executeBatch();
                    }
                });
        if (ctprev.get() != 0) stm.executeBatch();

        //Then the stations using a batch
        ctprev.set(0);
        stations.stream()
                .map(TransStation::stripSchedule)
                .distinct()
                .map(station -> String.format("INSERT INTO %s(%s, %s) VALUES (\'%s\', ST_POINT(%f,%f)::geography) ON CONFLICT DO NOTHING;",
                        PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_NAME_KEY, PostgresqlContract.STATION_LATLNG_KEY,
                        station.getName()
                                .replace("'", "`"), station.getCoordinates()[0], station
                                .getCoordinates()[1])
                )
                .forEach((LoggingUtils.WrappedConsumer<String>) (sql1) -> {
                    stm.addBatch(sql1);
                    if (ctprev.incrementAndGet() >= BATCH_SIZE) {
                        ctprev.getAndSet(0);
                        stm.executeBatch();
                    }
                });

        //Insert schedule information
        ctprev.set(0);
        stations.stream()
                .flatMap(PostgresSqlSupport::createScheduleQuery)
                .forEach((LoggingUtils.WrappedConsumer<String>) (sql) -> {
                    stm.addBatch(sql);
                    if (ctprev.incrementAndGet() >= BATCH_SIZE) {
                        ctprev.getAndSet(0);
                        stm.executeBatch();
                    }
                });
        if (ctprev.get() != 0) stm.executeBatch();


        //Lastly put the range into the database
        String query = String.format("INSERT INTO %s(%s, %s, %s, %s) " +
                        "VALUES (ST_POINT(%f,%f)::geography, %f, to_timestamp(%d), INTERVAL '%d seconds') " +
                        "ON CONFLICT(%s, %s) DO UPDATE SET %s=%f, %s=to_timestamp(%d), %s=INTERVAL '%d seconds'",
                PostgresqlContract.RANGE_TABLE_NAME, PostgresqlContract.RANGE_LAT_KEY, PostgresqlContract.RANGE_BOX_KEY, PostgresqlContract.RANGE_TIME_KEY, PostgresqlContract.RANGE_FUZZ_KEY,
                center[0], center[1], (LocationUtils.milesToMeters(range) != Double.POSITIVE_INFINITY) ? LocationUtils
                        .milesToMeters(range) : 9e99, startTime.getUnixTime(), deltlong / 1000,
                PostgresqlContract.RANGE_LAT_KEY, PostgresqlContract.RANGE_TIME_KEY,
                PostgresqlContract.RANGE_BOX_KEY, LocationUtils.milesToMeters(range),
                PostgresqlContract.RANGE_TIME_KEY, startTime.getUnixTime(),
                PostgresqlContract.RANGE_FUZZ_KEY, deltlong / 1000
        );
        stm.execute(query);

        //And close
        stm.close();
        return true;
    }

    private static Stream<String> createScheduleQuery(TransStation station) {
        return Stream.concat(
                Stream.of(String.format("INSERT INTO %s(%s, %s) VALUES ((SELECT %s FROM %s WHERE %s='%s'), (SELECT %s FROM %s WHERE ST_Equals(%s::geometry, ST_POINT(%f,%f)::geography::geometry))) ON CONFLICT DO NOTHING;",
                        PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_CHAIN_KEY, PostgresqlContract.COST_STATION_KEY,
                        PostgresqlContract.CHAIN_ID_KEY, PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_NAME_KEY, station
                                .getChain()
                                .getName()
                                .replace("'", "`"),
                        PostgresqlContract.STATION_ID_KEY, PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_LATLNG_KEY, station
                                .getCoordinates()[0], station.getCoordinates()[1]
                )),
                station.getSchedule().stream().map(sched -> String.format(
                        "\nWITH\n" +
                                "kost(%s) AS (\n" +
                                "SELECT %s.%s FROM %s \n" +
                                "INNER JOIN %s ON %s.%s=%s.%s \n" +
                                "INNER JOIN %s ON %s.%s=%s.%s \n" +
                                "WHERE %s.%s='%s' AND " +
                                "ST_DWITHIN(%s.%s, ST_POINT(%f,%f)::geography, %d, FALSE)" +
                                ") \n" +
                                "INSERT INTO %s(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)\n" +
                                "VALUES (%s, %s, %s, %s, %s, %s, %s, '%d:%d:%d', %d, (SELECT %s FROM kost LIMIT 1)) ON CONFLICT DO NOTHING;\n",
                        PostgresqlContract.COST_ID_KEY,
                        PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_ID_KEY, PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME,
                        PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_CHAIN_KEY, PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_ID_KEY,
                        PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.COST_STATION_KEY, PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_ID_KEY,
                        PostgresqlContract.CHAIN_TABLE_NAME, PostgresqlContract.CHAIN_NAME_KEY, station
                                .getChain()
                                .getName()
                                .replace("'", "`"),
                        PostgresqlContract.STATION_TABLE_NAME, PostgresqlContract.STATION_LATLNG_KEY, station
                                .getCoordinates()[0], station.getCoordinates()[1], ERROR_MARGIN,
                        PostgresqlContract.SCHEDULE_TABLE_NAME, PostgresqlContract.SCHEDULE_SUNDAY_VALID_KEY, PostgresqlContract.SCHEDULE_MONDAY_VALID_KEY,
                        PostgresqlContract.SCHEDULE_TUESDAY_VALID_KEY, PostgresqlContract.SCHEDULE_WEDNESDAY_VALID_KEY, PostgresqlContract.SCHEDULE_THURSDAY_VALID_KEY,
                        PostgresqlContract.SCHEDULE_FRIDAY_VALID_KEY, PostgresqlContract.SCHEDULE_SATURDAY_VALID_KEY, PostgresqlContract.SCHEDULE_TIME_KEY,
                        PostgresqlContract.SCHEDULE_FUZZ_KEY, PostgresqlContract.SCHEDULE_COST_ID_KEY,
                        sched.getValidDays()[0] ? "true" : "false", sched.getValidDays()[1] ? "true" : "false", sched
                                .getValidDays()[2] ? "true" : "false",
                        sched.getValidDays()[3] ? "true" : "false", sched.getValidDays()[4] ? "true" : "false", sched
                                .getValidDays()[5] ? "true" : "false",
                        sched.getValidDays()[6] ? "true" : "false", sched.getHour(), sched
                                .getMinute(), sched.getSecond(), sched.getFuzz(),
                        PostgresqlContract.COST_ID_KEY
                ))
        );

    }
}
