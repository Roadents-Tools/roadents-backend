package org.tymit.projectdonut.stations.postgresql;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.distance.DistanceUnits;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.utils.LoggingUtils;
import org.tymit.projectdonut.utils.ProfilingUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by ilan on 3/28/17.
 */
public class PostgresSqlSupport {


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
    public static final String TAG = "Postgres";
    private static final Distance MAX_NO_REQUERY = new Distance(250, DistanceUnits.METERS);


    /**
     * INFORMATION RETRIEVAL
     **/

    public static List<TransStation> getStrippedStations(Supplier<Connection> connectionSupplier,
                                                         double[] center, double range, int limit
    ) throws SQLException {
        if (range <= MAX_NO_REQUERY.inMiles()) {
            return getStrippedStationsSingle(connectionSupplier, center, range, limit);
        }

        double effectiverange = MAX_NO_REQUERY.inMiles();
        List<TransStation> rval = getStrippedStationsSingle(connectionSupplier, center, effectiverange, limit);
        while (rval.size() < limit && effectiverange <= range) {
            if (range / effectiverange <= 2) {
                effectiverange *= 2;
            } else {
                effectiverange = range;
            }
            rval = getStrippedStationsSingle(connectionSupplier, center, effectiverange, limit);
        }

        return rval;
    }

    private static List<TransStation> getStrippedStationsSingle(Supplier<Connection> connectionSupplier,
                                                                double[] center, double range, int limit
    ) throws SQLException {
        Connection con = connectionSupplier.get();
        Statement stm = con.createStatement();
        String query = String.format(
                "SELECT ST_X(%s::geometry) AS %s, ST_Y(%s::geometry) AS %s, %s " +
                        "FROM %s " +
                        "WHERE ST_DWITHIN(%s, ST_POINT(%f, %f)::geography, %f) " +
                        "LIMIT %d",
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_COL_NAME,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_COL_NAME,
                PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.StationTable.TABLE_NAME,
                PostgresqlContract.StationTable.LATLNG_KEY, center[0], center[1], new Distance(range, DistanceUnits.MILES)
                        .inMeters(),
                limit
        );

        LoggingUtils.logMessage(TAG, "Query type 1: %s", query);
        ProfilingUtils.MethodTimer extm = ProfilingUtils.startTimer("getStrippedStations::query");
        ResultSet rs = stm.executeQuery(query);
        extm.stop();

        ProfilingUtils.MethodTimer ptm = ProfilingUtils.startTimer("getStrippedStations::parse");
        List<TransStation> rval = new LinkedList<>();
        while (rs.isBeforeFirst()) rs.next();
        do {
            String name = rs.getString(PostgresqlContract.StationTable.NAME_KEY);
            double lat = rs.getDouble(LAT_COL_NAME);
            double lng = rs.getDouble(LNG_COL_NAME);
            rval.add(new TransStation(name, new double[] { lat, lng }));
        } while (rs.next());
        ptm.stop();

        return rval;
    }

    public static List<TransStation> getInformation(Supplier<Connection> connectionGenerator,
                                                    double[] center, double range,
                                                    TimePoint startTime, TimeDelta maxDelta,
                                                    TransChain chain, boolean checkRange
    ) throws SQLException {
        if (center == null && startTime == null && chain != null) return getChain(connectionGenerator, chain);
        Connection con = connectionGenerator.get();
        if (checkRange && !isValidQuery(con, center, range, startTime, maxDelta, chain)) return Collections.emptyList();


        ProfilingUtils.MethodTimer qtm = ProfilingUtils.startTimer("getInformation::query");
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(buildQuery(center, range, startTime, maxDelta, chain));
        qtm.stop();

        ProfilingUtils.MethodTimer ptm = ProfilingUtils.startTimer("getInformation::parse");
        Set<TransStation> rval = parseJoinedViewRs(rs);
        ptm.stop();
        return new ArrayList<>(rval);
    }

    private static String buildQuery(double[] center, double range,
                                     TimePoint startTime, TimeDelta maxDelta,
                                     TransChain chain
    ) {
        ProfilingUtils.MethodTimer tm = ProfilingUtils.startTimer("buildQuery");
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT * FROM " + PostgresqlContract.JOIN_VIEW_NAME + " ");
        if ((startTime != null && maxDelta != null) || (center != null && range >= 0) || chain != null) {
            builder.append("WHERE ");
        }
        if (chain != null) {
            if (!builder.toString().endsWith("WHERE ")) {
                builder.append(" AND ");
            }
            builder.append(String.format(" %s=\'%s\' ",
                    CHN_NAM_KEY, chain.getName().replace("'", "`")
            ));
        }
        if (center != null && range >= 0) {
            if (range == 0) range = 0.001;
            if (!builder.toString().endsWith("WHERE ")) {
                builder.append(" AND ");
            }
            builder.append(String.format(" ST_DWITHIN(%s, ST_POINT(%f, %f)::geography, %f) ",
                    PostgresqlContract.StationTable.LATLNG_KEY, center[0], center[1], new Distance(range, DistanceUnits.MILES)
                            .inMeters()
            ));
        }
        if (startTime != null && maxDelta != null) {
            if (!builder.toString().endsWith("WHERE ")) {
                builder.append(" AND ");
            }
            builder.append(String.format(" (%s, %s * INTERVAL \'1 ms\') OVERLAPS (\'%d:%d:%d\', INTERVAL '%d milliseconds') ",
                    PostgresqlContract.ScheduleTable.TIME_KEY,
                    PostgresqlContract.ScheduleTable.FUZZ_KEY,
                    startTime.getHour(), startTime.getMinute(), startTime.getSecond(), maxDelta.getDeltaLong()
            ));
        }
        tm.stop();
        return builder.toString();
    }

    private static boolean isValidQuery(Connection con,
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
                PostgresqlContract.RangeTable.ID_KEY, PostgresqlContract.RangeTable.TABLE_NAME,
                center[0], center[1], PostgresqlContract.RangeTable.LAT_KEY, new Distance(range, DistanceUnits.MILES).inMeters(),
                PostgresqlContract.RangeTable.TIME_KEY, startTime.getUnixTime(),
                PostgresqlContract.RangeTable.TIME_KEY, PostgresqlContract.RangeTable.FUZZ_KEY,
                startTime.plus(maxDelta).getUnixTime()
        ));
        return rs.next();
    }

    public static List<TransStation> getChain(Supplier<Connection> connectionGenerator, TransChain chain) throws SQLException {
        String query = String.format("SELECT * FROM %s WHERE %s=%s",
                PostgresqlContract.JOIN_VIEW_NAME, CHN_NAM_KEY, chain.getName());

        Connection con = connectionGenerator.get();

        ProfilingUtils.MethodTimer qtm = ProfilingUtils.startTimer("getChain::query");
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(query);
        qtm.stop();

        ProfilingUtils.MethodTimer ptm = ProfilingUtils.startTimer("getChain::parse");
        Set<TransStation> rval = parseJoinedViewRs(rs);
        ptm.stop();
        return new ArrayList<>(rval);

    }

    public static Set<TransStation> parseJoinedViewRs(ResultSet rs) throws SQLException {
        Map<Integer, TransStation> idToStations = new HashMap<>();
        Map<Integer, TransChain> idToChain = new HashMap<>();
        Map<Integer, Map<Integer, List<SchedulePoint>>> chainStationSchedule = new HashMap<>();

        while (rs.isBeforeFirst()) rs.next();
        do {
            int statId = rs.getInt(PostgresqlContract.CostTable.COST_STATION_KEY);
            if (!idToStations.containsKey(statId)) {
                TransStation nstat = new TransStation(rs.getString(STAT_NAM_KEY), new double[] { rs.getDouble(LAT_COL_NAME), rs.getDouble(LNG_COL_NAME) });
                idToStations.put(statId, nstat);
            }
            int chainId = rs.getInt(PostgresqlContract.CostTable.COST_CHAIN_KEY);
            if (!idToChain.containsKey(chainId)) {
                TransChain nchn = new TransChain(rs.getString(CHN_NAM_KEY));
                idToChain.put(chainId, nchn);
            }
            chainStationSchedule.putIfAbsent(chainId, new HashMap<>());
            chainStationSchedule.get(chainId)
                    .putIfAbsent(statId, new ArrayList<>());
            int validDays = rs.getInt(PostgresqlContract.ScheduleTable.PACKED_VALID_KEY);
            SchedulePoint nsched = new SchedulePoint(
                    rs.getInt(HOUR_COL_NAME),
                    rs.getInt(MINUTE_COL_NAME),
                    rs.getInt(SECOND_COL_NAME),

                    //New view system returns packed valid as number.
                    //TODO: Fix either the view or the field.
                    new boolean[] {
                            (validDays & 1) != 0,
                            (validDays / 10 & 1) != 0,
                            (validDays / 100 & 1) != 0,
                            (validDays / 1000 & 1) != 0,
                            (validDays / 10000 & 1) != 0,
                            (validDays / 100000 & 1) != 0,
                            (validDays / 1000000 & 1) != 0,
                    },
                    rs.getLong(PostgresqlContract.ScheduleTable.FUZZ_KEY)
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
        return rval;
    }

    /**
     * INFORMATION STORING
     **/

    public static boolean storeStations(Supplier<Connection> conGenerator, Collection<? extends TransStation> stations
    ) throws SQLException {
        Connection con = conGenerator.get();
        Statement stm = con.createStatement();

        //Insert the chains into the database in a batch
        AtomicInteger ctprev = new AtomicInteger(0);
        stations.stream()
                .map(TransStation::getChain)
                .filter(Objects::nonNull)
                .distinct()
                .map(TransChain::getName)
                .map(chainName -> String.format("INSERT INTO %s(%s) VALUES ('%s') ON CONFLICT(%s) DO NOTHING;",
                        PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, chainName
                                .replace("'", "`"), PostgresqlContract.ChainTable.NAME_KEY)
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
                        PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.NAME_KEY, PostgresqlContract.StationTable.LATLNG_KEY,
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

        stm.close();
        return true;
    }

    public static boolean storeArea(Supplier<Connection> conGenerator,
                                    double[] center, double range,
                                    TimePoint startTime, TimeDelta maxDelta,
                                    Collection<? extends TransStation> stations
    ) throws SQLException {

        Connection con = conGenerator.get();
        long deltlong = (MAX_POSTGRES_INTERVAL_MILLI > maxDelta.getDeltaLong()) ? maxDelta
                .getDeltaLong() : MAX_POSTGRES_INTERVAL_MILLI;
        Statement stm = con.createStatement();

        Distance rangeDist = new Distance(range, DistanceUnits.MILES);

        //Insert the chains into the database in a batch
        AtomicInteger ctprev = new AtomicInteger(0);
        stations.stream()
                .map(TransStation::getChain)
                .filter(Objects::nonNull)
                .distinct()
                .map(TransChain::getName)
                .map(chainName -> String.format("INSERT INTO %s(%s) VALUES ('%s') ON CONFLICT(%s) DO NOTHING;",
                        PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, chainName
                                .replace("'", "`"), PostgresqlContract.ChainTable.NAME_KEY)
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
                        PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.NAME_KEY, PostgresqlContract.StationTable.LATLNG_KEY,
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
                PostgresqlContract.RangeTable.TABLE_NAME, PostgresqlContract.RangeTable.LAT_KEY, PostgresqlContract.RangeTable.BOX_KEY, PostgresqlContract.RangeTable.TIME_KEY, PostgresqlContract.RangeTable.FUZZ_KEY,
                center[0], center[1], (rangeDist.inMeters() != Double.POSITIVE_INFINITY) ? rangeDist.inMeters() : 9e99,
                startTime.getUnixTime(), deltlong / 1000,
                PostgresqlContract.RangeTable.LAT_KEY, PostgresqlContract.RangeTable.TIME_KEY,
                PostgresqlContract.RangeTable.BOX_KEY, rangeDist.inMeters(),
                PostgresqlContract.RangeTable.TIME_KEY, startTime.getUnixTime(),
                PostgresqlContract.RangeTable.FUZZ_KEY, deltlong / 1000
        );
        stm.execute(query);

        //And close
        stm.close();
        return true;
    }

    private static Stream<String> createScheduleQuery(TransStation station) {
        return Stream.concat(
                Stream.of(String.format("INSERT INTO %s(%s, %s) VALUES ((SELECT %s FROM %s WHERE %s='%s'), (SELECT %s FROM %s WHERE ST_Equals(%s::geometry, ST_POINT(%f,%f)::geography::geometry))) ON CONFLICT DO NOTHING;",
                        PostgresqlContract.CostTable.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.CostTable.COST_CHAIN_KEY, PostgresqlContract.CostTable.COST_STATION_KEY,
                        PostgresqlContract.ChainTable.ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, station
                                .getChain()
                                .getName()
                                .replace("'", "`"),
                        PostgresqlContract.StationTable.ID_KEY, PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.LATLNG_KEY, station
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
                                "INSERT INTO %s(%s, %s, %s, %s)\n" +
                                "VALUES (B'%s', '%d:%d:%d', %d, (SELECT %s FROM kost LIMIT 1)) ON CONFLICT DO NOTHING;\n",
                        PostgresqlContract.CostTable.COST_ID_KEY,
                        PostgresqlContract.CostTable.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.CostTable.COST_ID_KEY, PostgresqlContract.CostTable.STATION_CHAIN_COST_TABLE_NAME,
                        PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.CostTable.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.CostTable.COST_CHAIN_KEY, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.ID_KEY,
                        PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.CostTable.STATION_CHAIN_COST_TABLE_NAME, PostgresqlContract.CostTable.COST_STATION_KEY, PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                        PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, station.getChain()
                                .getName()
                                .replace("'", "`"),
                        PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.LATLNG_KEY, station.getCoordinates()[0], station
                                .getCoordinates()[1], ERROR_MARGIN,
                        PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.PACKED_VALID_KEY, PostgresqlContract.ScheduleTable.TIME_KEY, PostgresqlContract.ScheduleTable.FUZZ_KEY, PostgresqlContract.ScheduleTable.COST_ID_KEY,
                        booleanArrToBitString(sched.getValidDays()), sched.getHour(), sched.getMinute(), sched.getSecond(), sched
                                .getFuzz(),
                        PostgresqlContract.CostTable.COST_ID_KEY
                ))
        );
    }

    private static String booleanArrToBitString(boolean[] bols) {
        StringBuilder builder = new StringBuilder();
        for (boolean bol : bols) {
            builder.append(bol ? '1' : '0');
        }
        return builder.toString();
    }
}
