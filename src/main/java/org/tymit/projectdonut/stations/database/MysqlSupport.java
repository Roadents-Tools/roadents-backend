package org.tymit.projectdonut.stations.database;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Created by ilan on 7/31/16.
 */
public class MysqlSupport {


    /**
     * Constants for Database Math
     **/
    public static final double ERROR_MARGIN = 0.0001;
    private static final int QUERY_LIMIT = 300;
    private static final double MILES_TO_LAT = 1.0 / 69.5;
    private static final double MILES_TO_LONG = 1/69.5;

    public static Map<Integer, TransStation> getStationIdMap(Connection connection, double[] center, double range, TransChain chain) throws SQLException {
        StringJoiner whereclause = new StringJoiner(" AND ");
        if (chain != null) {
            whereclause.add(MysqlContract.CHAIN_NAME_KEY + "=\"" + chain.getName()
                    .replaceAll("'", "\\'") + "\"");
        }

        if (center != null && range < 0) {
            return new HashMap<>();
        } else if (center != null && range == 0) {
            whereclause.add(String.format("%s=%f", MysqlContract.STATION_LAT_KEY, center[0]));
            whereclause.add(String.format("%s=%f", MysqlContract.STATION_LONG_KEY, center[1]));
        } else if (center != null && range > 0) {
            whereclause.add(stationCenterRangeBoxWhere(center, range) + " LIMIT " + QUERY_LIMIT);
        }

        String query = "SELECT * FROM " + MysqlContract.STATION_CHAIN_COST_TABLE_NAME
                + " INNER JOIN " + MysqlContract.STATION_TABLE_NAME + " ON " + MysqlContract.COST_STATION_KEY + " = " + MysqlContract.STATION_ID_KEY
                + " INNER JOIN " + MysqlContract.CHAIN_TABLE_NAME + " ON " + MysqlContract.COST_CHAIN_KEY + " = " + MysqlContract.CHAIN_ID_KEY
                + ((whereclause.length() > 0) ? " WHERE " + whereclause.toString() : "");

        Statement stm = connection.createStatement();

        ResultSet rawOut = stm.executeQuery(query);

        Map<Integer, TransChain> allChains = new HashMap<>();
        Map<Integer, TransStation> rawStations = new HashMap<>();
        Map<Integer, TransStation> stationsWithSchedule = new HashMap<>();

        while (rawOut.next()) {
            int id = rawOut.getInt(MysqlContract.STATION_ID_KEY);
            TransStation fromRow = getStationFromRow(rawOut, allChains);
            if (fromRow == null) continue;
            if (center == null) {
                rawStations.put(id, fromRow);
                continue;
            }
            double dist = LocationUtils.distanceBetween(center, fromRow.getCoordinates(), true);
            if (dist <= range + ERROR_MARGIN) rawStations.put(id, fromRow);
        }
        stm.close();
        rawOut.close();

        List<Integer> stationIDs = new ArrayList<>(allChains.keySet());
        List<Integer> chainIDs = new ArrayList<>(rawStations.keySet());
        Map<String, List<SchedulePoint>> schedules = getSchedules(connection, stationIDs, chainIDs);
        for (String keyPair : schedules.keySet()) {
            int stationId = Integer.valueOf(keyPair.split(",")[0]);
            int chainId = Integer.valueOf(keyPair.split(",")[1]);

            TransChain chainForSchedule = allChains.get(chainId);
            List<SchedulePoint> schedule = schedules.get(keyPair);
            TransStation stationWithSchedule = rawStations.get(stationId)
                    .withSchedule(chainForSchedule, schedule);
            stationsWithSchedule.put(stationId, stationWithSchedule);
        }

        return stationsWithSchedule;
    }

    public static Map<String, List<SchedulePoint>> getSchedules(Connection con, List<Integer> stationids, List<Integer> chainids) throws SQLException {

        Map<String, List<SchedulePoint>> rval = new ConcurrentHashMap<>();

        Statement stm = con.createStatement();

        String stationwhere = stationids.parallelStream()
                .map(num -> "" + num)
                .collect(() -> new StringJoiner(","), StringJoiner::add, StringJoiner::merge)
                .toString();

        String chainwhere = chainids.parallelStream()
                .map(num -> "" + num)
                .collect(() -> new StringJoiner(","), StringJoiner::add, StringJoiner::merge)
                .toString();

        String query = String.format("SELECT * FROM %s WHERE %s IN {%s} AND %s IN {%s}",
                MysqlContract.SCHEDULE_TABLE_NAME,
                MysqlContract.SCHEDULE_STATION_KEY, stationwhere,
                MysqlContract.SCHEDULE_CHAIN_KEY, chainwhere
        );

        ResultSet rs = stm.executeQuery(query);

        while (rs.next()) {
            int stationId = rs.getInt(MysqlContract.SCHEDULE_STATION_KEY);
            int chainId = rs.getInt(MysqlContract.SCHEDULE_CHAIN_KEY);
            String key = stationId + "," + chainId;
            rval.putIfAbsent(key, new ArrayList<>());

            int hour = rs.getInt(MysqlContract.SCHEDULE_HOUR_KEY);
            int minute = rs.getInt(MysqlContract.SCHEDULE_MINUTE_KEY);
            int second = rs.getInt(MysqlContract.SCHEDULE_SECOND_KEY);
            long fuzz = rs.getInt(MysqlContract.SCHEDULE_FUZZ_KEY);

            boolean[] validDays = new boolean[] {
                    rs.getBoolean(MysqlContract.SCHEDULE_SUNDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_MONDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_TUESDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_WEDNESDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_THURSDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_FRIDAY_VALID_KEY),
                    rs.getBoolean(MysqlContract.SCHEDULE_SATURDAY_VALID_KEY)
            };
            rval.get(key)
                    .add(new SchedulePoint(hour, minute, second, validDays, fuzz));
        }

        return rval;

    }

    private static String stationCenterRangeBoxWhere(double[] center, double range) {

        double latVal1 = center[0] - range * MILES_TO_LAT;
        double latVal2 = center[0] + range * MILES_TO_LAT;

        double lngVal1 = center[1] - range * MILES_TO_LONG;
        double lngVal2 = center[1] + range * MILES_TO_LONG;

        return String.format(" %s BETWEEN %f AND %f AND %s BETWEEN %f AND %f",
                MysqlContract.STATION_LAT_KEY, Math.min(latVal1, latVal2), Math.max(latVal1, latVal2),
                MysqlContract.STATION_LONG_KEY, Math.min(lngVal1, lngVal2), Math
                        .max(lngVal1, lngVal2));
    }

    public static TransStation getStationFromRow(ResultSet currentRow, Map<Integer, TransChain> availableChains) throws SQLException {

        String name = currentRow.getString(MysqlContract.STATION_NAME_KEY);
        double[] latlong = new double[] { currentRow.getDouble(MysqlContract.STATION_LAT_KEY), currentRow.getDouble(MysqlContract.STATION_LONG_KEY) };

        int chainId = currentRow.getInt(MysqlContract.COST_CHAIN_KEY);
        if (availableChains.get(chainId) == null) {
            String chainName = currentRow.getString(MysqlContract.CHAIN_NAME_KEY);
            TransChain chain = new TransChain(chainName);
            availableChains.put(chainId, chain);
        }

        return new TransStation(name, latlong);

    }

    public static boolean insertOrUpdateStations(Supplier<Connection> conGen, Collection<? extends TransStation> stations) {
        //Declare a cache that maps chain -> Id.
        //We do this to a) only insert chains if we need to and
        // b) so that we don't run out of memory.
        LoadingCache<TransChain, Integer> chainToId = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build(new CacheLoader<TransChain, Integer>() {
                    @Override
                    public Integer load(TransChain key) throws Exception {
                        Connection con = conGen.get();
                        int id = insertOrGetChain(con, key);
                        con.close();
                        return id;
                    }
                });

        //Ditto for the stations.
        LoadingCache<TransStation, Integer> stationToId = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build(new CacheLoader<TransStation, Integer>() {
                           @Override
                           public Integer load(TransStation key) throws Exception {
                               Connection con = conGen.get();
                               int id = insertOrGetStation(con, key);
                               con.close();
                               return id;
                           }
                       }
                );

        //Insert new schedules and costs
        return stations.parallelStream().map(station -> {
            try {
                Connection con = conGen.get();
                insertOrUpdateSchedule(con, stationToId.get(station.stripSchedule()), chainToId
                        .get(station.getChain()), station.getSchedule());
                addCost(con, stationToId.get(station.stripSchedule()), chainToId
                        .get(station.getChain()));
                con.close();
                return true;
            } catch (Exception e) {
                LoggingUtils.logError(e);
                return false;
            }
        }).allMatch(Boolean::booleanValue);
    }

    public static int insertOrGetChain(Connection connection, TransChain newChain) throws SQLException {
        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s",
                MysqlContract.CHAIN_ID_KEY, MysqlContract.CHAIN_TABLE_NAME, MysqlContract.CHAIN_NAME_KEY, "\"" + newChain
                        .getName()
                        .replaceAll("'", "\\'") + "\"");

        Statement stm = connection.createStatement();
        ResultSet rs = stm.executeQuery(idQuery);
        if (rs.next()) {
            return rs.getInt(MysqlContract.CHAIN_ID_KEY);
        }
        rs.close();
        stm.close();

        String insertQuery = String.format("INSERT INTO %s (%s) VALUES (%s)",
                MysqlContract.CHAIN_TABLE_NAME, MysqlContract.CHAIN_NAME_KEY, "\"" + newChain
                        .getName()
                        .replaceAll("'", "\\'") + "\"");
        stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();

        stm = connection.createStatement();
        rs = stm.executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        int id = rs.getInt(MysqlContract.CHAIN_ID_KEY);
        rs.close();
        stm.close();
        return id;
    }

    public static int insertOrGetStation(Connection connection, TransStation station) throws SQLException {
        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s AND %s=%f AND %s=%f",
                MysqlContract.STATION_ID_KEY, MysqlContract.STATION_TABLE_NAME, MysqlContract.STATION_NAME_KEY, "\"" + station
                        .getName()
                        .replaceAll("'", "\\'") + "\"",
                MysqlContract.STATION_LAT_KEY, station.getCoordinates()[0], MysqlContract.STATION_LONG_KEY, station
                        .getCoordinates()[1]
        );
        Statement stm = connection.createStatement();
        ResultSet rs = stm.executeQuery(idQuery);
        if (rs.next()) {
            int id = rs.getInt(MysqlContract.STATION_ID_KEY);
            rs.close();
            stm.close();
            return id;
        }

        String insertQuery = String.format("INSERT INTO %s (%s,%s,%s) VALUES (%s,%f,%f)",
                MysqlContract.STATION_TABLE_NAME, MysqlContract.STATION_NAME_KEY, MysqlContract.STATION_LAT_KEY, MysqlContract.STATION_LONG_KEY,
                "\"" + station.getName().replaceAll("'", "\\'") + "\"", station.getCoordinates()[0], station.getCoordinates()[1]
        );

        stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();

        stm = connection.createStatement();
        rs = stm.executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        int id = rs.getInt(MysqlContract.STATION_ID_KEY);
        rs.close();
        stm.close();
        return id;
    }

    public static void insertOrUpdateSchedule(Connection con, int stationId, int chainId, List<SchedulePoint> schedule) throws SQLException {
        deleteSchedule(con, stationId, chainId);
        addCost(con, stationId, chainId);
        Statement stm = con.createStatement();
        schedule.parallelStream()
                .map(pt -> String.format(
                        "INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) VALUES (%d, %d, %d, %d, %d, %d, %b, %b, %b, %b, %b, %b, %b)",
                        MysqlContract.SCHEDULE_TABLE_NAME, MysqlContract.SCHEDULE_STATION_KEY, MysqlContract.SCHEDULE_CHAIN_KEY,
                        MysqlContract.SCHEDULE_HOUR_KEY, MysqlContract.SCHEDULE_MINUTE_KEY, MysqlContract.SCHEDULE_SECOND_KEY,
                        MysqlContract.SCHEDULE_FUZZ_KEY, MysqlContract.SCHEDULE_SUNDAY_VALID_KEY, MysqlContract.SCHEDULE_MONDAY_VALID_KEY,
                        MysqlContract.SCHEDULE_TUESDAY_VALID_KEY, MysqlContract.SCHEDULE_WEDNESDAY_VALID_KEY, MysqlContract.SCHEDULE_THURSDAY_VALID_KEY,
                        MysqlContract.SCHEDULE_FRIDAY_VALID_KEY, MysqlContract.SCHEDULE_SATURDAY_VALID_KEY,
                        stationId, chainId, pt.getHour(), pt.getMinute(), pt.getSecond(), pt
                                .getFuzz(),
                        pt.getValidDays()[0], pt.getValidDays()[1], pt.getValidDays()[2], pt
                                .getValidDays()[3], pt.getValidDays()[4],
                        pt.getValidDays()[5], pt.getValidDays()[6]))
                .forEach((sql) -> {
                    try {
                        stm.addBatch(sql);
                    } catch (SQLException e) {
                        throw Throwables.propagate(e);
                    }
                });
        stm.executeBatch();
        stm.close();
    }

    public static void deleteSchedule(Connection con, int stationId, int chainId) throws SQLException {

        String deleteQuery = String.format("DELETE FROM %s WHERE %s=%d AND %s=%d",
                MysqlContract.SCHEDULE_TABLE_NAME, MysqlContract.SCHEDULE_STATION_KEY, stationId, MysqlContract.SCHEDULE_CHAIN_KEY, chainId);

        Statement stm = con.createStatement();
        stm.execute(deleteQuery);
        stm.close();
    }

    public static void addCost(Connection con, int stationId, int chainId) throws SQLException {
        Statement stm = con.createStatement();
        stm.execute(String.format("INSERT INTO %s (%s, %s) VALUES (%d, %d)",
                MysqlContract.STATION_CHAIN_COST_TABLE_NAME,
                MysqlContract.COST_CHAIN_KEY, MysqlContract.COST_STATION_KEY,
                chainId, stationId
        ));
        stm.close();
    }
}
