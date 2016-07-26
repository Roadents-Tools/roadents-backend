package org.tymit.projectdonut.stations.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/9/16.
 */
public class MysqlStationDb implements StationDbInstance {

    public static final String[] DB_URLS = new String[]{"jdbc:mysql://192.168.1.71:3306/Donut"};
    private static final double ERROR_MARGIN = 0.0001;
    private static final String USER = "donut";
    private static final String PASS = "donutpass";

    private static final String STATION_TABLE_NAME = "transstation";
    private static final String STATION_ID_KEY = STATION_TABLE_NAME + ".id";
    private static final String STATION_NAME_KEY = STATION_TABLE_NAME + ".name";
    private static final String STATION_LAT_KEY = STATION_TABLE_NAME + ".latitude";
    private static final String STATION_LONG_KEY = STATION_TABLE_NAME + ".longitude";

    private static final String CHAIN_TABLE_NAME = "transchain";
    private static final String CHAIN_NAME_KEY = CHAIN_TABLE_NAME + ".name";
    private static final String CHAIN_ID_KEY = CHAIN_TABLE_NAME + ".id";

    private static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
    private static final String COST_ID_KEY = STATION_CHAIN_COST_TABLE_NAME + ".id";
    private static final String COST_STATION_KEY = STATION_CHAIN_COST_TABLE_NAME + ".stationId";
    private static final String COST_CHAIN_KEY = STATION_CHAIN_COST_TABLE_NAME + ".chainId";
    private static final String COST_SCHEDULE_KEY = STATION_CHAIN_COST_TABLE_NAME + ".scheduleJson";

    private Connection connection;

    public MysqlStationDb(String url) {
        try {
            connection = DriverManager.getConnection(url, USER, PASS);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            connection = null;
        }
    }

    public void clearDb() throws SQLException {
        for (String table : new String[]{STATION_TABLE_NAME, CHAIN_TABLE_NAME, STATION_CHAIN_COST_TABLE_NAME}) {
            connection.createStatement().executeUpdate("DELETE FROM " + table);
        }
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {
        Map<TransStation, Integer> queryOut = null;
        try {
            queryOut = getStationIdMap(center, range, chain);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
        }
        if (queryOut == null) return null;
        return new ArrayList<>(queryOut.keySet());
    }

    private Map<TransStation, Integer> getStationIdMap(double[] center, double range, TransChain chain) throws SQLException {
        String whereclause = "";
        if (chain != null) {
            whereclause += " WHERE " + CHAIN_NAME_KEY + "='" + chain.getName() + "'";
        }

        String query = "SELECT * FROM " + STATION_CHAIN_COST_TABLE_NAME
                + " INNER JOIN " + STATION_TABLE_NAME + " ON " + COST_STATION_KEY + " = " + STATION_ID_KEY
                + " INNER JOIN " + CHAIN_TABLE_NAME + " ON " + COST_CHAIN_KEY + " = " + CHAIN_ID_KEY
                + whereclause;
        ResultSet rawOut = connection.createStatement().executeQuery(query);
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
            if (range < 0) continue;
            double dist = LocationUtils.distanceBetween(center, fromRow.getCoordinates(), true);
            if (dist <= range + ERROR_MARGIN) outMap.put(fromRow, id);
        }

        return outMap;
    }

    private TransStation getStationFromRow(ResultSet currentRow, Map<String, TransChain> availableChains) {
        try {
            String name = currentRow.getString(STATION_NAME_KEY);
            double[] latlong = new double[]{currentRow.getDouble(STATION_LAT_KEY), currentRow.getDouble(STATION_LONG_KEY)};

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<TimeModel> decodeSchedule(String encodedSchedule) {
        List<TimeModel> schedule = new ArrayList<>();
        Gson gson = new Gson();
        JsonParser parser = new JsonParser();

        new ArrayList<>();
        JsonArray array = parser.parse(encodedSchedule).getAsJsonArray();
        for (JsonElement elm : array) {
            TimeModel model = gson.fromJson(elm, TimeModel.class);
            schedule.add(model);
        }
        return schedule;
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        try {
            //Insert new chains
            Map<String, Integer> allChains = getExistingChains();
            Set<TransChain> newChains = stations.stream()
                    .map(station -> station.getChain())
                    .filter(chain -> !allChains.containsKey(chain.getName()))
                    .collect(Collectors.toSet());
            for (TransChain chain : newChains) {
                int newId = insertNewChain(chain);
                allChains.put(chain.getName(), newId);
            }

            //Insert new stations
            Map<TransStation, Integer> allStations = getStationIdMap(null, 0, null);
            Set<TransStation> toInsert = stations.stream()
                    .filter(station -> !allStations.containsValue(station))
                    .collect(Collectors.toSet());
            for (TransStation newStation : toInsert) {
                int newId = insertNewStation(newStation);
                allStations.put(newStation, newId);
            }

            //Insert new schedules and costs
            Map<Integer, Map<Integer, String>> existingCosts = getExistingSchedules();
            for (TransStation station : stations) {
                int stationId = allStations.get(station);
                int chainId = allChains.get(station.getChain().getName());
                String encodedSchedule = encodeSchedule(station.getSchedule());
                if (existingCosts.get(chainId) == null || existingCosts.get(chainId).get(stationId) == null) {
                    insertNewCosts(stationId, chainId, encodedSchedule);
                } else if (!existingCosts.get(chainId).get(stationId).equals(encodedSchedule)) {
                    updateCosts(stationId, chainId, encodedSchedule);
                }
            }
            return true;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return false;
        }
    }

    @Override
    public boolean isUp() {
        try {
            return connection != null && !connection.isClosed() && !connection.isReadOnly();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            return false;
        }
    }

    private Map<String, Integer> getExistingChains() throws SQLException {
        Map<String, Integer> chainData = new HashMap<>();
        String query = "SELECT * FROM " + CHAIN_TABLE_NAME;
        ResultSet rawOut = connection.createStatement().executeQuery(query);
        while (rawOut.next()) {
            chainData.put(rawOut.getString(CHAIN_NAME_KEY), rawOut.getInt(CHAIN_ID_KEY));
        }
        return chainData;
    }

    private int insertNewChain(TransChain newChain) throws SQLException {
        String insertQuery = String.format("INSERT INTO %s (%s) VALUES (%s)",
                CHAIN_TABLE_NAME, CHAIN_NAME_KEY, "'" + newChain.getName() + "'"
        );
        connection.createStatement().executeUpdate(insertQuery);

        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s",
                CHAIN_ID_KEY, CHAIN_TABLE_NAME, CHAIN_NAME_KEY, "'" + newChain.getName() + "'"
        );
        ResultSet rs = connection.createStatement().executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        return rs.getInt(CHAIN_ID_KEY);
    }

    private int insertNewStation(TransStation station) throws SQLException {
        String insertQuery = String.format("INSERT INTO %s (%s,%s,%s) VALUES (%s,%f,%f)",
                STATION_TABLE_NAME, STATION_NAME_KEY, STATION_LAT_KEY, STATION_LONG_KEY,
                "'" + station.getName() + "'", station.getCoordinates()[0], station.getCoordinates()[1]
        );
        connection.createStatement().executeUpdate(insertQuery);

        String idQuery = String.format("SELECT %s FROM %s WHERE %s=%s AND %s=%f AND %s=%f",
                STATION_ID_KEY, STATION_TABLE_NAME, STATION_NAME_KEY, "'" + station.getName() + "'",
                STATION_LAT_KEY, station.getCoordinates()[0], STATION_LONG_KEY, station.getCoordinates()[1]
        );
        ResultSet rs = connection.createStatement().executeQuery(idQuery);
        while (rs.isBeforeFirst()) rs.next();
        return rs.getInt(STATION_ID_KEY);
    }

    private Map<Integer, Map<Integer, String>> getExistingSchedules() throws SQLException {
        Map<Integer, Map<Integer, String>> rval = new HashMap<>();
        String query = "SELECT * FROM " + STATION_CHAIN_COST_TABLE_NAME;
        ResultSet rawOut = connection.createStatement().executeQuery(query);
        while (rawOut.next()) {
            int chainId = rawOut.getInt(COST_CHAIN_KEY);
            int stationId = rawOut.getInt(COST_STATION_KEY);
            String schedule = rawOut.getString(COST_SCHEDULE_KEY);
            if (rval.get(chainId) == null) rval.put(chainId, new HashMap<>());
            rval.get(chainId).put(stationId, schedule);
        }
        return rval;
    }

    private static String encodeSchedule(List<TimeModel> schedule) {
        Gson gson = new Gson();
        return gson.toJson(schedule);
    }

    private void insertNewCosts(int stationId, int chainId, String encodedSchedule) throws SQLException {
        String insertQuery = String.format("INSERT INTO %s (%s,%s,%s) VALUES (%d,%d,%s)",
                STATION_CHAIN_COST_TABLE_NAME, COST_STATION_KEY, COST_CHAIN_KEY, COST_SCHEDULE_KEY,
                stationId, chainId, "'" + encodedSchedule + "'"
        );

        connection.createStatement().executeUpdate(insertQuery);
    }

    private void updateCosts(int stationId, int chainId, String encodedSchedule) throws SQLException {
        String insertQuery = String.format("UPDATE %s SET %s = %s WHERE %s=%d AND %s=%d",
                STATION_CHAIN_COST_TABLE_NAME, COST_SCHEDULE_KEY, "'" + encodedSchedule + "'",
                COST_STATION_KEY, stationId, COST_CHAIN_KEY, chainId
        );

        connection.createStatement().executeUpdate(insertQuery);
    }
}
