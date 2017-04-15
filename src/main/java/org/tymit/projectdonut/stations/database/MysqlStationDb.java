package org.tymit.projectdonut.stations.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
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
public class MysqlStationDb implements StationDbInstance.AreaDb {

    public static final String[] DB_URLS = new String[] { "jdbc:mysql://127.0.0.1:3306/Donut" };
    private static final String USER = "donut";
    private static final String PASS = "donutpass";
    private HikariDataSource connSource;
    private boolean isUp;

    public MysqlStationDb(String url) {
        isUp = true;
        try {
            connSource = new HikariDataSource(initSource(url));
        } catch (HikariPool.PoolInitializationException e) {
            LoggingUtils.logError(e);
            isUp = false;
        }
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
                if (connection == null) throw new Exception("Null connection.");
                if (!connection.createStatement().execute(deleteQuery)) success = false;
                connection.close();
            } catch (Exception e) {
                LoggingUtils.logError(e);
                success = false;
            }
        }
        return success;
    }

    private Connection getConnection() {
        try {
            return connSource.getConnection();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return null;
        }
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TransChain chain) {

        Connection connection = getConnection();
        if (connection == null) return null;

        Map<Integer, TransStation> queryOut = null;
        try {
            queryOut = MysqlSupport.getStationIdMap(connection, center, range, chain);
            connection.close();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
        }
        if (queryOut == null) return null;
        return new ArrayList<>(queryOut.values());
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        return MysqlSupport.insertOrUpdateStations(this::getConnection, stations);
    }

    @Override
    public boolean isUp() {
        return isUp;
    }

    @Override
    public void close() {
        connSource.close();
    }
}
