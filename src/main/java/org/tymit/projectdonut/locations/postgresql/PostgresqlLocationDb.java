package org.tymit.projectdonut.locations.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.tymit.projectdonut.locations.interfaces.LocationProvider;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by ilan on 7/9/17.
 */
public class PostgresqlLocationDb implements LocationProvider {


    public static final String[] DB_URLS = new String[] { "jdbc:postgresql://donutdb.c3ovzbdvtevz.us-west-2.rds.amazonaws.com:5432/Donut" };
    private static final String USER = "donut";
    private static final String PASS = "donutpass";
    private HikariDataSource connSource;
    private boolean isUp;

    public PostgresqlLocationDb(String url) {

        isUp = true;
        try {
            Class.forName("org.postgresql.Driver");
            connSource = new HikariDataSource(initSource(url));
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isUp = false;
        }
    }

    private HikariConfig initSource(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(USER);
        config.setPassword(PASS);
        config.setMaximumPoolSize(1);
        return config;
    }

    public Connection getConnection() {
        try {
            return connSource.getConnection();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            return null;
        }
    }

    @Override
    public boolean isUsable() {
        return isUp;
    }

    @Override
    public boolean isValidType(LocationType type) {
        return false;
    }

    @Override
    public List<DestinationLocation> queryLocations(double[] center, double range, LocationType type) {
        return null;
    }

    public void close() {
        connSource.close();
    }
}
