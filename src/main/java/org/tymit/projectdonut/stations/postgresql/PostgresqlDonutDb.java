package org.tymit.projectdonut.stations.postgresql;

import org.tymit.projectdonut.model.database.DatabaseID;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;
import org.tymit.projectdonut.utils.StreamUtils;
import org.tymit.projectdonut.utils.TimeUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

public class PostgresqlDonutDb implements StationDbInstance.DonutDb {

    public static final String[] DB_URLS = new String[] {
            "jdbc:postgresql://donutdb.c3ovzbdvtevz.us-west-2.rds.amazonaws.com:5432/DonutDump",
            "jdbc:postgresql://192.168.1.71:5432/Donut"
    };
    private static final String TAG = "PostgresDonutDb";
    private static final String USER = "donut";
    private static final String PASS = "donutpass";

    private static final String LAT_KEY = "statlatman";
    private static final String LNG_KEY = "statlngman";
    private static final String HOUR_KEY = "hourman";
    private static final String MINUTE_KEY = "minuteman";
    private static final String SECOND_KEY = "secondman";
    private static final String CHAIN_NAME_KEY = "chanm";
    private static final String STATION_NAME_KEY = "statnm";
    private static final String DELTA_KEY = "deltaman";

    private static final long BATCH_SIZE = 300;
    private static final long DRIVING_METERS_PER_HOUR = 100000;
    private static final long MILLIS_IN_HOUR = 3600000;

    private static final double METERS_PER_MILLI = DRIVING_METERS_PER_HOUR * 1. / MILLIS_IN_HOUR;

    private final Connection con;
    private final String url;
    private boolean isUp;

    public PostgresqlDonutDb(String url) {
        isUp = true;
        this.url = url;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isUp = false;
        }

        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASS);
        props.setProperty("ssl", "true");
        props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
        props.setProperty("sslcompression", "true");

        Connection tempcon;
        try {
            tempcon = DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            tempcon = null;
        }
        con = tempcon;
    }

    private Connection getConnection() {
        return con;
    }

    @Override
    public boolean isUp() {
        return isUp;
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        if (!isUp) return false;
        try {
            Connection con1 = getConnection();
            Statement stm = con1.createStatement();

            //Insert the chains into the database in a batch
            AtomicInteger ctprev = new AtomicInteger(0);
            ((Collection<? extends TransStation>) stations).stream()
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
            ((Collection<? extends TransStation>) stations).stream()
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
            ((Collection<? extends TransStation>) stations).stream()
                    .flatMap(station -> {

                        String insertHeader = "INSERT INTO " + PostgresqlContract.ScheduleTable.TABLE_NAME + "(" +
                                PostgresqlContract.ScheduleTable.PACKED_VALID_KEY + ", " +
                                PostgresqlContract.ScheduleTable.TIME_KEY + ", " +
                                PostgresqlContract.ScheduleTable.FUZZ_KEY + ", " +
                                PostgresqlContract.ScheduleTable.PACKED_TIME_KEY + ", " +
                                PostgresqlContract.ScheduleTable.STATION_ID_KEY + ", " +
                                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY + ")";

                        return station.getSchedule().stream()
                                .map(spt -> insertHeader + String.format(
                                        "VALUES (B'%s', '%d:%d:%d', %d, %d, (SELECT %s FROM %s WHERE %s=%s AND ST_DWITHIN(%s, ST_POINT(%f, %f)::geography), 1), (SELECT %s FROM %s WHERE %s=%s))",

                                        StreamUtils.streamBoolArray(spt.getValidDays())
                                                .map(bol -> bol ? '1' : '0')
                                                .collect(StreamUtils.joinToString("")),

                                        spt.getHour(), spt.getMinute(), spt.getSecond(),
                                        spt.getFuzz(),
                                        TimeUtils.packSchedulePoint(spt),

                                        PostgresqlContract.StationTable.ID_KEY, PostgresqlContract.StationTable.TABLE_NAME,
                                        PostgresqlContract.StationTable.TABLE_NAME, station.getName(),
                                        PostgresqlContract.StationTable.LATLNG_KEY, station.getCoordinates()[0], station
                                                .getCoordinates()[1],

                                        PostgresqlContract.ChainTable.ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME,
                                        PostgresqlContract.ChainTable.NAME_KEY, station.getChain().getName()
                                ));
                    })
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
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return false;
        }
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (!isUp || con == null || center == null || range.inMeters() < 0) return Collections.emptyList();
        LoggingUtils.logMessage(TAG, "Hit DB for area query (%f, %f), %f meters.",
                center.getCoordinates()[0], center.getCoordinates()[1], range.inMeters()
        );

        String query = String.format(
                "SELECT %s, %s, ST_X(%s::geometry) AS %s, ST_Y(%s::geometry) AS %s FROM %s " +
                        "WHERE ST_DWITHIN(%s, ST_POINT(%f, %f)::geography, %f)",
                PostgresqlContract.StationTable.ID_KEY, PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY, PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.LATLNG_KEY,
                center.getCoordinates()[0], center.getCoordinates()[1], range.inMeters()
        );

        List<TransStation> rval = new ArrayList<>();

        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);
            while (rs.isBeforeFirst()) rs.next();
            do {
                int id = rs.getInt(PostgresqlContract.StationTable.ID_KEY);
                String name = rs.getString(PostgresqlContract.StationTable.NAME_KEY);
                double lat = rs.getDouble(LAT_KEY);
                double lng = rs.getDouble(LNG_KEY);
                if (LocationUtils.distanceBetween(new StartPoint(new double[] { lat, lng }), center).inMeters() <= range
                        .inMeters()) {
                    rval.add(new TransStation(name, new double[] { lat, lng }, new DatabaseID(url, "" + id)));
                }
            } while (rs.next());

            rs.close();
            stm.close();
            return rval;
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyList();
        }
    }

    public void close() {
        isUp = false;
        try {
            con.close();
        } catch (Exception e) {
            LoggingUtils.logError(e);
        }
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        if (!isUp || con == null || station == null || station.getID() == null || !url.equals(station.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }

        LoggingUtils.logMessage(TAG, "Hit DB for chains query %s, (%f, %f)", station.getName(),
                station.getCoordinates()[0], station.getCoordinates()[1]);

        String query = String.format(
                "SELECT %s.%s, %s, %s, " +
                        "EXTRACT('hour' FROM %s) AS %s, " +
                        "EXTRACT('minute' FROM %s) AS %s, " +
                        "EXTRACT('second' FROM %s) AS %s " +
                        "FROM %s, %s " +
                        "WHERE %s = %s AND %s = %s.%s",
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.NAME_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, HOUR_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, MINUTE_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, SECOND_KEY,
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ChainTable.TABLE_NAME,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, station.getID().getId(),
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME,
                PostgresqlContract.ChainTable.ID_KEY
        );

        Map<TransChain, List<SchedulePoint>> rval = new HashMap<>();

        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);
            while (rs.isBeforeFirst()) rs.next();

            Map<Integer, TransChain> chainstore = new HashMap<>();
            do {
                SchedulePoint pt = new SchedulePoint(
                        rs.getInt(HOUR_KEY),
                        rs.getInt(MINUTE_KEY),
                        rs.getInt(SECOND_KEY),
                        null,
                        60,
                        new DatabaseID(url, "" + rs.getInt(PostgresqlContract.ScheduleTable.ID_KEY))
                );

                int chainid = rs.getInt(PostgresqlContract.ScheduleTable.CHAIN_ID_KEY);

                TransChain chain = chainstore.get(chainid);
                if (chain == null) {
                    chain = new TransChain(
                            rs.getString(PostgresqlContract.ChainTable.NAME_KEY),
                            new DatabaseID(url, "" + chainid)
                    );
                    chainstore.put(chainid, chain);
                }

                rval.computeIfAbsent(chain, k -> new ArrayList<>()).add(pt);

            } while (rs.next());

            rs.close();
            stm.close();
            return rval;

        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(List<TransChain> chains, TimePoint startTime, TimeDelta maxDelta) {

        if (!isUp || con == null || chains == null || chains.isEmpty() || chains.stream()
                .map(chain -> chain.getID().getDatabaseName())
                .anyMatch(db -> !url.equals(db))) {
            return Collections.emptyMap();
        }

        Map<Integer, TransChain> idToChain = chains.stream()
                .collect(StreamUtils.collectWithKeys(chain -> Integer.parseInt(chain.getID().getId())));

        String idgroup = idToChain.keySet().stream()
                .map(id -> "" + id)
                .collect(() -> new StringJoiner(", "), StringJoiner::add, StringJoiner::merge)
                .toString();

        String query = String.format(
                "SELECT %s, %s, %s, " +
                        "ST_X(%s::geometry) AS %s, " +
                        "ST_Y(%s::geometry) AS %s, " +
                        "EXTRACT('epoch' FROM %s - '%d:%d:%d') AS %s " +
                        "FROM %s, %s " +
                        "WHERE %s=%s.%s AND %s IN (%s) AND %s",
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,

                PostgresqlContract.ScheduleTable.TIME_KEY, startTime.getHour(), startTime.getMinute(), startTime.getSecond(),
                DELTA_KEY,

                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.StationTable.TABLE_NAME,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, idgroup, buildTimeQuery(startTime, maxDelta)
        );


        Map<TransChain, Map<TransStation, TimeDelta>> rval = new HashMap<>();
        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);

            Map<Integer, TransStation> stationstore = new HashMap<>();
            while (rs.isBeforeFirst()) rs.next();
            do {
                int chainid = rs.getInt(PostgresqlContract.ScheduleTable.CHAIN_ID_KEY);
                TransChain key = idToChain.get(chainid);

                int stationid = rs.getInt(PostgresqlContract.ScheduleTable.STATION_ID_KEY);
                TransStation station = stationstore.get(stationid);
                if (station == null) {
                    station = new TransStation(
                            rs.getString(PostgresqlContract.StationTable.NAME_KEY),
                            new double[] { rs.getDouble(LAT_KEY), rs.getDouble(LNG_KEY) },
                            new DatabaseID(url, "" + stationid)
                    );
                    stationstore.put(stationid, station);
                }

                int rawDelta = rs.getInt(DELTA_KEY);
                int delta = 1000 * (rawDelta >= 0 ? rawDelta : 24 * 60 * 60 + rawDelta);
                TimeDelta deltaObj = new TimeDelta(delta);

                rval.computeIfAbsent(key, c -> new HashMap<>())
                        .compute(station, (station1, existing) -> (existing == null || existing.getDeltaLong() > deltaObj
                                .getDeltaLong())
                                ? deltaObj
                                : existing
                        );
            } while (rs.next());

            return rval;
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<TransStation, Map<TransChain, List<SchedulePoint>>> getChainsForStations(List<TransStation> stations) {
        if (stations == null || stations.isEmpty() || !isUp || con == null || stations.stream()
                .map(station -> station.getID().getDatabaseName())
                .anyMatch(dbname -> !url.equals(dbname))) {
            return Collections.emptyMap();
        }

        Map<Integer, TransStation> idToStation = stations.stream()
                .collect(StreamUtils.collectWithKeys(station -> Integer.parseInt(station.getID().getId())));

        String stationGroup = idToStation.keySet().stream()
                .map(id -> "" + id)
                .collect(() -> new StringJoiner(", "), StringJoiner::add, StringJoiner::merge)
                .toString();

        String query = String.format(
                "SELECT %s.%s, %s, %s, %s, " +
                        "EXTRACT('hour' FROM %s) AS %s, " +
                        "EXTRACT('minute' FROM %s) AS %s, " +
                        "EXTRACT('second' FROM %s) AS %s " +
                        "FROM %s, %s " +
                        "WHERE %s IN (%s) AND %s = %s.%s",
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.NAME_KEY,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, HOUR_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, MINUTE_KEY,
                PostgresqlContract.ScheduleTable.TIME_KEY, SECOND_KEY,
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ChainTable.TABLE_NAME,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, stationGroup,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME,
                PostgresqlContract.ChainTable.ID_KEY
        );

        System.out.printf("Query: %s\n", query);

        Map<TransStation, Map<TransChain, List<SchedulePoint>>> rval = new HashMap<>();

        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);
            while (rs.isBeforeFirst()) rs.next();

            Map<Integer, TransChain> chainstore = new HashMap<>();
            do {
                SchedulePoint pt = new SchedulePoint(
                        rs.getInt(HOUR_KEY),
                        rs.getInt(MINUTE_KEY),
                        rs.getInt(SECOND_KEY),
                        null,
                        60,
                        new DatabaseID(url, "" + rs.getInt(PostgresqlContract.ScheduleTable.ID_KEY))
                );

                int chainid = rs.getInt(PostgresqlContract.ScheduleTable.CHAIN_ID_KEY);

                TransChain chain = chainstore.get(chainid);
                if (chain == null) {
                    chain = new TransChain(
                            rs.getString(PostgresqlContract.ChainTable.NAME_KEY),
                            new DatabaseID(url, "" + chainid)
                    );
                    chainstore.put(chainid, chain);
                }

                int stationid = rs.getInt(PostgresqlContract.ScheduleTable.STATION_ID_KEY);
                TransStation stat = idToStation.get(stationid);

                rval.computeIfAbsent(stat, s -> new HashMap<>())
                        .computeIfAbsent(chain, c -> new ArrayList<>())
                        .add(pt);

            } while (rs.next());

            rs.close();
            stm.close();
            return rval;

        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (!isUp || con == null || chain == null || chain.getID() == null || !url.equals(chain.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }

        int[] testIDs = new int[] {
                5240,
                11196,
                19743,
                23630,
                27236,
                29912,
                47521,
                50761

        };

        String query = String.format(
                "SELECT %s, %s, " +
                        "ST_X(%s::geometry) AS %s, " +
                        "ST_Y(%s::geometry) AS %s, " +
                        "EXTRACT('epoch' FROM %s - '%d:%d:%d') AS %s " +
                        "FROM %s, %s " +
                        "WHERE %s=%s.%s AND %s=%s AND %s",
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,

                PostgresqlContract.ScheduleTable.TIME_KEY, startTime.getHour(), startTime.getMinute(), startTime.getSecond(),
                DELTA_KEY,

                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.StationTable.TABLE_NAME,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, chain.getID().getId(),
                buildTimeQuery(startTime, maxDelta)
        );

        if (Arrays.stream(testIDs).anyMatch(id -> chain.getID().getId().equals("" + id))) {
            LoggingUtils.logMessage(TAG, "Query for %s: \n%s", chain.getID().getId(), query);
        }

        Map<TransStation, TimeDelta> rval = new HashMap<>();
        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);

            while (rs.isBeforeFirst()) rs.next();
            do {
                int stid = rs.getInt(PostgresqlContract.ScheduleTable.STATION_ID_KEY);
                String name = rs.getString(PostgresqlContract.StationTable.NAME_KEY);
                double lat = rs.getDouble(LAT_KEY);
                double lng = rs.getDouble(LNG_KEY);
                TransStation station = new TransStation(
                        name,
                        new double[] { lat, lng },
                        new DatabaseID(url, "" + stid)
                );

                long rawDelta = rs.getLong(DELTA_KEY);
                long delta = 1000 * (rawDelta > 0 ? rawDelta : 86400 + rawDelta);
                TimeDelta deltaObj = new TimeDelta(delta);

                rval.put(station, deltaObj);

                if (Arrays.stream(testIDs).anyMatch(id -> chain.getID().getId().equals("" + id))) {
                    LoggingUtils.logMessage(TAG, "Chain: %s\nStart: %d\nRawDelta: %d\nDelta: %d", chain.getID()
                            .getId(), startTime.getUnixTime(), rawDelta, delta);
                }

            } while (rs.next());

            return rval;
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }

    }

    private static String buildTimeQuery(TimePoint startTime, TimeDelta maxDelta) {

        if (maxDelta.getDeltaLong() >= (24 * 60 * 60 * 1000L)) {
            return String.format(" %s BETWEEN %d AND %d",
                    "packedtime", 0, 86400
            );
        }

        TimePoint endTime = startTime.plus(maxDelta);
        if (endTime.getHour() >= startTime.getHour()) {
            return String.format(" %s BETWEEN %d AND %d",
                    "packedtime",
                    TimeUtils.packTimePoint(startTime),
                    TimeUtils.packTimePoint(endTime)
            );
        } else {
            return String.format(" (%s BETWEEN %d AND %d OR %s BETWEEN 0 AND %d)",
                    "packedtime",
                    TimeUtils.packTimePoint(startTime),
                    86399,
                    "packedtime",
                    TimeUtils.packTimePoint(endTime)
            );
        }
    }

    @Override
    public Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(LocationPoint center, TimePoint startTime, TimeDelta maxDelta) {

        LoggingUtils.logMessage(TAG, "Building world around (%f,%f), %d-%d-%d %d:%d:%d + %d seconds.",
                center.getCoordinates()[0], center.getCoordinates()[1],
                startTime.getYear(), startTime.getMonth(), startTime.getDayOfMonth(),
                startTime.getHour(), startTime.getMinute(), startTime.getSecond(),
                maxDelta.getDeltaLong() / 1000L);

        String query = String.format("SELECT " +
                        "%s.%s, " +
                        "%s, " +
                        "%s, " +
                        "ST_X(%s::geometry) AS %s, " +
                        "ST_Y(%s::geometry) AS %s, " +
                        "%s, " +
                        "%s, " +
                        "%s.%s AS %s, \n" +
                        "%s.%s AS %s \n" +
                        "FROM %s, %s, %s \n" +
                        "WHERE ST_DWITHIN(%s, ST_POINT(%f,%f)::geography, %f) \n" +
                        "AND %s \n" +
                        "AND %s = %s.%s \n" +
                        "AND %s = %s.%s",
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,
                PostgresqlContract.ScheduleTable.PACKED_TIME_KEY,
                PostgresqlContract.ScheduleTable.PACKED_VALID_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.NAME_KEY, STATION_NAME_KEY,
                PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, CHAIN_NAME_KEY,

                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.StationTable.TABLE_NAME,

                PostgresqlContract.StationTable.LATLNG_KEY, center.getCoordinates()[0], center.getCoordinates()[1], maxDelta
                        .getDeltaLong() * METERS_PER_MILLI,
                buildTimeQuery(startTime, maxDelta),
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.ID_KEY

        );

        Map<Integer, TransStation> stationstore = new HashMap<>();
        Map<Integer, TransChain> chainstore = new HashMap<>();
        Map<TransChain, Map<TransStation, List<SchedulePoint>>> rval = new HashMap<>();
        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);

            while (rs.isBeforeFirst()) rs.next();
            do {
                int chainid = rs.getInt(PostgresqlContract.ScheduleTable.CHAIN_ID_KEY);
                TransChain chain = chainstore.get(chainid);
                if (chain == null) {
                    chain = new TransChain(rs.getString(CHAIN_NAME_KEY), new DatabaseID(url, "" + chainid));
                    chainstore.put(chainid, chain);
                }

                int stationid = rs.getInt(PostgresqlContract.ScheduleTable.STATION_ID_KEY);
                TransStation station = stationstore.get(stationid);
                if (station == null) {
                    station = new TransStation(
                            rs.getString(STATION_NAME_KEY),
                            new double[] { rs.getDouble(LAT_KEY), rs.getDouble(LNG_KEY) },
                            new DatabaseID(url, "" + stationid)
                    );
                    stationstore.put(stationid, station);
                }

                int time = rs.getInt(PostgresqlContract.ScheduleTable.PACKED_TIME_KEY);
                String daysString = rs.getString(PostgresqlContract.ScheduleTable.PACKED_VALID_KEY);
                boolean[] validDays = new boolean[daysString.length()];
                for (int i = 0; i < validDays.length; i++) {
                    validDays[i] = daysString.charAt(i) == '1';
                }

                SchedulePoint pt = TimeUtils.unpackPoint(
                        time,
                        validDays,
                        60,
                        new DatabaseID(url, "" + rs.getInt(PostgresqlContract.ScheduleTable.ID_KEY))
                );

                rval.computeIfAbsent(chain, c -> new HashMap<>())
                        .computeIfAbsent(station, s -> new ArrayList<>())
                        .add(pt);
            } while (rs.next());

            return rval;

        } catch (SQLException e) {
            isUp = false;
            LoggingUtils.logError(e);
            return Collections.emptyMap();
        }
    }

    public String getSourceName() {
        return url;
    }
}
