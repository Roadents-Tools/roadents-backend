package org.tymit.projectdonut.stations.database;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/9/16.
 */
public class MysqlStationDb implements StationDbInstance {

    public static final String[] DB_URLS = new String[] { "jdbc:mysql://127.0.0.1:3306/Donut" };
    private static final String USER = "donut";
    private static final String PASS = "donutpass";
    private final HikariDataSource connSource;
    private boolean isUp;

    public MysqlStationDb(String url) {
        isUp = true;
        connSource = new HikariDataSource(initSource(url));
    }

    private HikariConfig initSource(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(USER);
        config.setPassword(PASS);
        config.setMaximumPoolSize(10);
        return config;
    }

    public boolean removeItems(String whereClause, String[] tables) {
        boolean success = true;

        for (String table : tables) {
            String deleteQuery = "DELETE FROM " + table + " WHERE " + whereClause;
            try {
                Connection connection = getConnection();
                if (!connection.createStatement().execute(deleteQuery)) success = false;
                connection.close();
            } catch (SQLException e) {
                LoggingUtils.logError(e);
                success = false;
            }
        }
        return success;
    }

    private Connection getConnection() throws SQLException {
        return connSource.getConnection();
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {
        Connection connection;
        try {
            connection = getConnection();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return null;
        }

        Map<TransStation, Integer> queryOut = null;
        try {
            queryOut = MysqlSupport.getStationIdMap(connection, center, range, chain);
            connection.close();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
        }
        if (queryOut == null) return null;
        return new ArrayList<>(queryOut.keySet());
    }

    @Override
    public boolean putStations(List<TransStation> stations) {

        //Declare a cache that maps chain -> Id.
        //We do this to a) only insert chains if we need to and
        // b) so that we don't run out of memory.
        LoadingCache<TransChain, Integer> chainToId = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build(new CacheLoader<TransChain, Integer>() {
                    @Override
                    public Integer load(TransChain key) throws Exception {
                        Connection con = getConnection();
                        int id = MysqlSupport.insertOrGetChain(con, key);
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
                               Connection con = getConnection();
                               int id = MysqlSupport.insertOrGetStation(con, key);
                               con.close();
                               return id;
                           }
                       }
                );

            //Insert new schedules and costs
        return stations.stream().map(station -> {
            try {
                int stationId = stationToId.get(convertToKey(station));
                int chainId = chainToId.get(station.getChain());
                String encodedSchedule = MysqlSupport.encodeSchedule(station.getSchedule());
                Connection con = getConnection();
                MysqlSupport.insertOrUpdateCosts(con, stationId, chainId, encodedSchedule);
                con.close();
                return true;
            } catch (Exception e) {
                LoggingUtils.logError(e);
                return false;
            }
        }).allMatch(Boolean::booleanValue);
    }

    @Override
    public boolean isUp() {
        return isUp;
    }

    @Override
    public void close() {
        connSource.close();
    }

    public TransStation convertToKey(TransStation base) {
        return new TransStation(base.getName(), base.getCoordinates());
    }
}
