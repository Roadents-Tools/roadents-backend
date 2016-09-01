package org.tymit.projectdonut.stations.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Created by ilan on 7/31/16.
 */
public class MysqlSupport {

    /**
     * Database Table and Field Names
     **/
    public static final String CHAIN_TABLE_NAME = "transchain";
    public static final String CHAIN_ID_KEY = CHAIN_TABLE_NAME + ".id";
    public static final String CHAIN_NAME_KEY = CHAIN_TABLE_NAME + ".name";
    public static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
    public static final String COST_SCHEDULE_KEY = STATION_CHAIN_COST_TABLE_NAME + ".scheduleJson";
    public static final String COST_CHAIN_KEY = STATION_CHAIN_COST_TABLE_NAME + ".chainId";
    public static final String COST_STATION_KEY = STATION_CHAIN_COST_TABLE_NAME + ".stationId";
    public static final String COST_ID_KEY = STATION_CHAIN_COST_TABLE_NAME + ".id";
    public static final double ERROR_MARGIN = 0.0001;
    public static final String STATION_TABLE_NAME = "transstation";
    public static final String STATION_LONG_KEY = STATION_TABLE_NAME + ".longitude";
    public static final String STATION_LAT_KEY = STATION_TABLE_NAME + ".latitude";
    public static final String STATION_NAME_KEY = STATION_TABLE_NAME + ".name";
    public static final String STATION_ID_KEY = STATION_TABLE_NAME + ".id";


    private static final int RADIAL_STATION_LIMIT = 100;

    /**
     * Constants for Database Math
     **/
    private static final double MILES_TO_LAT = 1.0 / 69.5;
    private static final double MILES_TO_LONG = 1/69.5;

    public static Map<TransStation, Integer> getStationIdMap(Connection connection, double[] center, double range, TransChain chain) throws SQLException {
        StringJoiner whereclause = new StringJoiner(" AND ");
        if (chain != null) {
            whereclause.add(CHAIN_NAME_KEY + "=\"" + chain.getName().replaceAll("'", "\\'") + "\"");
        }

        if (center != null && range < 0) {
            return new HashMap<>();
        } else if (center != null && range == 0) {
            whereclause.add(String.format("%s=%f", STATION_LAT_KEY, center[0]));
            whereclause.add(String.format("%s=%f", STATION_LONG_KEY, center[1]));
        } else if (center != null && range > 0) {
            whereclause.add(stationCenterRangeBoxWhere(center, range) + " LIMIT " + RADIAL_STATION_LIMIT);
        }

        String query = "SELECT * FROM " + STATION_CHAIN_COST_TABLE_NAME
                + " INNER JOIN " + STATION_TABLE_NAME + " ON " + COST_STATION_KEY + " = " + STATION_ID_KEY
                + " INNER JOIN " + CHAIN_TABLE_NAME + " ON " + COST_CHAIN_KEY + " = " + CHAIN_ID_KEY
                + ((whereclause.length() > 0) ? " WHERE " + whereclause.toString() : "");

        Statement stm = connection.createStatement();
        ResultSet rawOut = stm.executeQuery(query);

        Map<String, TransChain> allChains = new HashMap<>();
        Map<TransStation, Integer> outMap = new HashMap<>();

        while (rawOut.next()) {
            int id = rawOut.getInt(STATION_ID_KEY);
            TransStation fromRow = getStationFromRow(rawOut, allChains);
            if (fromRow == null) continue;
            if (center == null) {
                outMap.put(fromRow, id);
                continue;
            }
            double dist = LocationUtils.distanceBetween(center, fromRow.getCoordinates(), true);
            if (dist <= range + ERROR_MARGIN) outMap.put(fromRow, id);
        }


        stm.close();
        rawOut.close();
        return outMap;
    }

    private static String stationCenterRangeBoxWhere(double[] center, double range) {

        double latVal1 = center[0] - range * MILES_TO_LAT;
        double latVal2 = center[0] + range * MILES_TO_LAT;

        double lngVal1 = center[1] - range * MILES_TO_LONG;
        double lngVal2 = center[1] + range * MILES_TO_LONG;

        return String.format(" %s BETWEEN %f AND %f AND %s BETWEEN %f AND %f",
                STATION_LAT_KEY, Math.min(latVal1, latVal2), Math.max(latVal1, latVal2),
                STATION_LONG_KEY, Math.min(lngVal1, lngVal2), Math.max(lngVal1, lngVal2));
    }

    public static TransStation getStationFromRow(ResultSet currentRow, Map<String, TransChain> availableChains) throws SQLException {

        String name = currentRow.getString(STATION_NAME_KEY);
        double[] latlong = new double[] { currentRow.getDouble(STATION_LAT_KEY), currentRow.getDouble(STATION_LONG_KEY) };

        String chainName = currentRow.getString(CHAIN_NAME_KEY);
        TransChain chain;
        if (availableChains.get(chainName) == null) {
            chain = new TransChain(chainName);
            availableChains.put(chainName, chain);
        } else {
            chain = availableChains.get(chainName);
        }

        String scheduleJson = currentRow.getString(COST_SCHEDULE_KEY);
        List<TimeModel> schedule = decodeSchedule(scheduleJson);

        TransStation rval = new TransStation(name, latlong, schedule, chain);
        return rval;

    }

    public static List<TimeModel> decodeSchedule(String encodedSchedule) {
        List<TimeModel> schedule = new ArrayList<>();
        Gson gson = new Gson();
        JsonParser parser = new JsonParser();

        JsonArray array = parser.parse(encodedSchedule).getAsJsonArray();
        for (JsonElement elm : array) {
            TimeModel model = gson.fromJson(elm, TimeModel.class);
            schedule.add(model);
        }
        return schedule;
    }

    public static String encodeSchedule(List<TimeModel> schedule) {
        Gson gson = new Gson();
        return gson.toJson(schedule);
    }

    public static int insertOrGetChain(Connection connection, TransChain newChain) throws SQLException {
        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s",
                CHAIN_ID_KEY, CHAIN_TABLE_NAME, CHAIN_NAME_KEY, "\"" + newChain.getName().replaceAll("'", "\\'") + "\"");

        Statement stm = connection.createStatement();
        ResultSet rs = stm.executeQuery(idQuery);
        if (rs.next()) {
            int id = rs.getInt(CHAIN_ID_KEY);
            return id;
        }
        rs.close();
        stm.close();

        String insertQuery = String.format("INSERT INTO %s (%s) VALUES (%s)",
                CHAIN_TABLE_NAME, CHAIN_NAME_KEY, "\"" + newChain.getName().replaceAll("'", "\\'") + "\"");
        stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();

        stm = connection.createStatement();
        rs = stm.executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        int id = rs.getInt(CHAIN_ID_KEY);
        rs.close();
        stm.close();
        return id;
    }

    public static int insertOrGetStation(Connection connection, TransStation station) throws SQLException {
        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s AND %s=%f AND %s=%f",
                STATION_ID_KEY, STATION_TABLE_NAME, STATION_NAME_KEY, "\"" + station.getName().replaceAll("'", "\\'") + "\"",
                STATION_LAT_KEY, station.getCoordinates()[0], STATION_LONG_KEY, station.getCoordinates()[1]
        );
        Statement stm = connection.createStatement();
        ResultSet rs = stm.executeQuery(idQuery);
        if (rs.next()) {
            int id = rs.getInt(STATION_ID_KEY);
            rs.close();
            stm.close();
            return id;
        }

        String insertQuery = String.format("INSERT INTO %s (%s,%s,%s) VALUES (%s,%f,%f)",
                STATION_TABLE_NAME, STATION_NAME_KEY, STATION_LAT_KEY, STATION_LONG_KEY,
                "\"" + station.getName().replaceAll("'", "\\'") + "\"", station.getCoordinates()[0], station.getCoordinates()[1]
        );

        stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();

        stm = connection.createStatement();
        rs = stm.executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        int id = rs.getInt(STATION_ID_KEY);
        rs.close();
        stm.close();
        return id;
    }

    public static void insertOrUpdateCosts(Connection connection, int stationId, int chainId, String encodedSchedule) throws SQLException {
        delectCosts(connection, stationId, chainId);
        insertNewCosts(connection, stationId, chainId, encodedSchedule);
    }

    public static void insertNewCosts(Connection connection, int stationId, int chainId, String encodedSchedule) throws SQLException {
        String insertQuery = String.format("INSERT INTO %s (%s,%s,%s) VALUES (%d,%d,%s)",
                STATION_CHAIN_COST_TABLE_NAME, COST_STATION_KEY, COST_CHAIN_KEY, COST_SCHEDULE_KEY,
                stationId, chainId, "\'" + encodedSchedule + "\'");

        Statement stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();
    }

    public static void delectCosts(Connection connection, int stationId, int chainId) throws SQLException {

        String deleteQuery = String.format("DELETE FROM %s WHERE %s=%d AND %s=%d",
                STATION_CHAIN_COST_TABLE_NAME, COST_STATION_KEY, stationId, COST_CHAIN_KEY, chainId);

        Statement stm = connection.createStatement();
        stm.execute(deleteQuery);
        stm.close();
    }

    public static void updateCosts(Connection connection, int stationId, int chainId, String encodedSchedule) throws SQLException {
        String insertQuery = String.format("UPDATE %s SET %s = %s WHERE %s=%d AND %s=%d",
                STATION_CHAIN_COST_TABLE_NAME, COST_SCHEDULE_KEY, "\"" + encodedSchedule.replaceAll("'", "\\'") + "\"",
                COST_STATION_KEY, stationId, COST_CHAIN_KEY, chainId
        );

        Statement stm = connection.createStatement();
        stm.executeUpdate(insertQuery);
        stm.close();
    }
}
