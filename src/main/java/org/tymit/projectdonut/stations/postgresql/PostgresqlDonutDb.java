package org.tymit.projectdonut.stations.postgresql;

import org.tymit.projectdonut.model.database.DatabaseID;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;
import org.tymit.projectdonut.utils.StreamUtils;

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
import java.util.Properties;
import java.util.StringJoiner;

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
            return PostgresSqlSupport.storeStations(this::getConnection, stations);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return false;
        }
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (!isUp || con == null || center == null || range.inMeters() < 0) return Collections.emptyList();

        String query = String.format(
                "SELECT %s, %s, ST_X(%s::geometry) AS %s, ST_Y(%s::geometry) AS %s FROM %s " +
                        "WHERE ST_DWITHIN(%s, ST_POINT(%f, %f)::geography, %f)",
                PostgresqlContract.StationTable.ID_KEY, PostgresqlContract.StationTable.NAME_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY, PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.LATLNG_KEY,
                center.getCoordinates()[0], center.getCoordinates()[1], range.inMeters()
        );

        LoggingUtils.logMessage(TAG, "Query: %s\n", query);
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
                if (LocationUtils.distanceBetween(new double[] { lat, lng }, center.getCoordinates(), true) <= range.inMiles()) {
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
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (!isUp || con == null || chain == null || chain.getID() == null || !url.equals(chain.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }

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

                int rawDelta = rs.getInt(DELTA_KEY);
                int delta = 1000 * (rawDelta >= 0 ? rawDelta : 86400 + rawDelta);
                TimeDelta deltaObj = new TimeDelta(delta);

                rval.put(station, deltaObj);

            } while (rs.next());

            return rval;
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return Collections.emptyMap();
        }

    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        if (!isUp || con == null || station == null || station.getID() == null || !url.equals(station.getID()
                .getDatabaseName())) {
            return Collections.emptyMap();
        }

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
                    startTime.getHour() * 3600 + startTime.getMinute() * 60 + startTime.getSecond(),
                    endTime.getHour() * 3600 + endTime.getMinute() * 60 + endTime.getSecond()
            );
        } else {
            return String.format(" (%s BETWEEN %d AND %d OR %s BETWEEN 0 AND %d)",
                    "packedtime",
                    startTime.getHour() * 3600 + startTime.getMinute() * 60 + startTime.getSecond(),
                    86399,
                    "packedtime",
                    endTime.getHour() * 3600 + endTime.getMinute() * 60 + endTime.getSecond()
            );
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

        System.out.printf("Query: %s\n", query);

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
                        .compute(station, (station1, timeDelta) -> (timeDelta == null || timeDelta.getDeltaLong() > deltaObj
                                .getDeltaLong())
                                ? deltaObj
                                : timeDelta
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
    public Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(LocationPoint center, TimePoint startTime, TimeDelta maxDelta) {

        LoggingUtils.logMessage(TAG, "Entering batch into request.");
        String query = String.format("SELECT %s.%s, %s, %s, " +
                        "ST_X(%s::geometry) AS %s, " +
                        "ST_Y(%s::geometry) AS %s, " +
                        "packedtime, " +
                        "%s.%s AS %s, \n" +
                        "%s.%s AS %s \n" +
                        "FROM %s, %s, %s \n" +
                        "WHERE ST_DWITHIN(%s, ST_POINT(%f,%f)::geography, %f) \n" +
                        "AND %s \n" +
                        "AND %s = %s.%s \n" +
                        "AND %s = %s.%s",
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ScheduleTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ScheduleTable.STATION_ID_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LAT_KEY,
                PostgresqlContract.StationTable.LATLNG_KEY, LNG_KEY,
                PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.NAME_KEY, STATION_NAME_KEY,
                PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.NAME_KEY, CHAIN_NAME_KEY,
                PostgresqlContract.ScheduleTable.TABLE_NAME, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.StationTable.TABLE_NAME,
                PostgresqlContract.StationTable.LATLNG_KEY, center.getCoordinates()[0], center.getCoordinates()[1], maxDelta
                        .getDeltaLong() * METERS_PER_MILLI,
                buildTimeQuery(startTime, maxDelta),
                PostgresqlContract.ScheduleTable.STATION_ID_KEY, PostgresqlContract.StationTable.TABLE_NAME, PostgresqlContract.StationTable.ID_KEY,
                PostgresqlContract.ScheduleTable.CHAIN_ID_KEY, PostgresqlContract.ChainTable.TABLE_NAME, PostgresqlContract.ChainTable.ID_KEY

        );
        LoggingUtils.logMessage(TAG, "%s", query);

        Map<Integer, TransStation> stationstore = new HashMap<>();
        Map<Integer, TransChain> chainstore = new HashMap<>();
        Map<TransChain, Map<TransStation, List<SchedulePoint>>> rval = new HashMap<>();
        LoggingUtils.logMessage(TAG, "Entering try loop.");
        try {
            Statement stm = con.createStatement();
            ResultSet rs = stm.executeQuery(query);

            LoggingUtils.logMessage(TAG, "Parsing results.");
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

                int time = rs.getInt("packedtime");
                SchedulePoint pt = new SchedulePoint(
                        (time / 3600) % 60,
                        (time / 60) % 60,
                        time % 60,
                        null,
                        60,
                        new DatabaseID(url, "" + rs.getInt(PostgresqlContract.ScheduleTable.ID_KEY))
                );

                rval.computeIfAbsent(chain, c -> new HashMap<>())
                        .computeIfAbsent(station, s -> new ArrayList<>())
                        .add(pt);
            } while (rs.next());

            LoggingUtils.logMessage(TAG, "Finishing method.");

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
