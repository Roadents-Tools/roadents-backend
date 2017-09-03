package com.reroute.backend.stations.postgresql;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.interfaces.StationDbInstance;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.TimeUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class PostgresqlDonutDb implements StationDbInstance.DonutDb {

    /* Database Constants */
    public static final String[] DB_URLS = new String[] {
            "jdbc:postgresql://donutdb.c3ovzbdvtevz.us-west-2.rds.amazonaws.com:5432/DonutDump",
            "jdbc:postgresql://192.168.1.71:5432/Donut"
    };
    private static final String TAG = "PostgresDonutDb";
    private static final String USER = "donut";
    private static final String PASS = "donutpass";

    /* Custom column labels */
    private static final String LAT_KEY = "statlatman";
    private static final String LNG_KEY = "statlngman";
    private static final String CHAIN_NAME_KEY = "chanm";
    private static final String STATION_NAME_KEY = "statnm";
    private static final String DELTA_KEY = "deltaman";

    /* Math constants */
    private static final long BATCH_SIZE = 300;

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

    /* Utility methods */

    @Override
    public boolean putStations(List<TransStation> stations) {
        if (!isUp) return false;
        try {
            Connection con1 = getConnection();
            Statement stm = con1.createStatement();

            //Insert the chains into the database in a batch
            AtomicInteger ctprev = new AtomicInteger(0);
            stations.stream()
                    .map(TransStation::getChain)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(TransChain::getName)
                    .map(chainName -> String.format("INSERT INTO %s(%s) VALUES ('%s') ON CONFLICT DO NOTHING;",
                            PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY,
                            chainName.replace("'", "`"))
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
                    .flatMap(station -> {
                        String insertHeader = "INSERT INTO " + PostgresqlContract.ScheduleTable.TABLE_NAME + "(" +
                                PostgresqlContract.ScheduleTable.PACKED_VALID_KEY + ", " +
                                PostgresqlContract.ScheduleTable.FUZZ_KEY + ", " +
                                PostgresqlContract.ScheduleTable.PACKED_TIME_KEY + ", " +
                                PostgresqlContract.ScheduleTable.STATION_ID_KEY + ", " +
                                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY + ")";
                        return station.getSchedule().stream()
                                .map(spt -> insertHeader + String.format(
                                        "VALUES (B'%s', %d, %d, " +
                                                "(SELECT %s FROM %s WHERE %s=\'%s\' AND ST_DWITHIN(%s, ST_POINT(%f, %f)::geography, .01) LIMIT 1), " +
                                                "(SELECT %s FROM %s WHERE %s=\'%s\' LIMIT 1))",

                                        TimeUtils.boolsToBitStr(spt.getValidDays()),
                                        spt.getFuzz(), TimeUtils.packSchedulePoint(spt),

                                        PostgresqlContract.StationTable.ID_KEY, PostgresqlContract.StationTable.TABLE_NAME,
                                        PostgresqlContract.StationTable.NAME_KEY, station.getName().replace("'", "`"),
                                        PostgresqlContract.StationTable.LATLNG_KEY, station.getCoordinates()[0], station
                                                .getCoordinates()[1],

                                        PostgresqlContract.ChainTable.ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME,
                                        PostgresqlContract.ChainTable.NAME_KEY, station.getChain()
                                                .getName()
                                                .replace("'", "`")
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
    public boolean isUp() {
        return isUp;
    }

    /**
     * Gets the instance's JDBC connection.
     *
     * @return the instance's JDBC connection
     */
    private Connection getConnection() {
        return con;
    }

    /**
     * Closes the database, including connection.
     */
    public void close() {
        isUp = false;
        try {
            con.close();
        } catch (Exception e) {
            LoggingUtils.logError(e);
        }
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (!isUp || con == null || center == null || range.isZero()) return Collections.emptyList();

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

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        if (!isUp || con == null || station == null || station.getID() == null || !url.equals(station.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }

        String query = String.format(
                "SELECT %s.%s, %s, %s, %s, %s " +
                        "FROM %s, %s " +
                        "WHERE %s = %s AND %s = %s.%s",
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.NAME_KEY,
                PostgresqlContract.ScheduleTable.PACKED_TIME_KEY, PostgresqlContract.ScheduleTable.PACKED_VALID_KEY,
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
                SchedulePoint pt = TimeUtils.unpackPoint(
                        rs.getInt(PostgresqlContract.ScheduleTable.PACKED_TIME_KEY),
                        TimeUtils.bitStrToBools(rs.getString(PostgresqlContract.ScheduleTable.PACKED_VALID_KEY)),
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
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (!isUp || con == null || chain == null || chain.getID() == null || !url.equals(chain.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }
        String query = String.format(
                "SELECT %s, %s, " +
                        "ST_X(%s::geometry) AS %s, " +
                        "ST_Y(%s::geometry) AS %s, " +
                        "(%s - %d) AS %s " +
                        "FROM %s, %s " +
                        "WHERE %s=%s.%s AND %s=%s AND %s",
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,

                PostgresqlContract.ScheduleTable.PACKED_TIME_KEY, TimeUtils.packTimePoint(startTime),
                DELTA_KEY,

                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.StationTable.TABLE_NAME,
                PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, chain.getID().getId(),
                buildTimeQuery(startTime, maxDelta)
        );

        Map<TransStation, TimeDelta> rval = new HashMap<>();
        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);

            while (rs.next()) {
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

            }

            return rval;
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }

    }

    @Override
    public Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta) {

        LoggingUtils.logMessage(TAG, "Building world around (%f,%f), %d-%d-%d %d:%d:%d + %d seconds.",
                center.getCoordinates()[0], center.getCoordinates()[1],
                startTime.getYear(), startTime.getMonth(), startTime.getDayOfMonth(),
                startTime.getHour(), startTime.getMinute(), startTime.getSecond(),
                maxDelta.getDeltaLong() / 1000L);

        int startday = startTime.getDayOfWeek();
        int endday = startTime.plus(maxDelta).getDayOfWeek();
        if (endday < startday) endday += 7;

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

                PostgresqlContract.StationTable.LATLNG_KEY, center.getCoordinates()[0], center.getCoordinates()[1], range
                        .inMeters(),
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

                boolean skipsked = IntStream.range(startday, endday + 1)
                        .boxed()
                        .map(i -> i % validDays.length)
                        .anyMatch(i -> validDays[i]);

                if (skipsked) continue;

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

    @Override
    public String getSourceName() {
        return url;
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
                    86400,
                    "packedtime",
                    TimeUtils.packTimePoint(endTime)
            );
        }
    }
}
