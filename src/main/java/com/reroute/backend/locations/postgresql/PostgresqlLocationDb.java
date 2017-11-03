package com.reroute.backend.locations.postgresql;

import com.reroute.backend.locations.interfaces.LocationProvider;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * A location provider using a Postgresql database as the backing store.
 */
public class PostgresqlLocationDb implements LocationProvider {


    public static final String[] DB_URLS = new String[] { "jdbc:postgresql://donutdb.c3ovzbdvtevz.us-west-2.rds.amazonaws.com:5432/Donut" };
    private static final String USER = "generator";
    private static final String PASS = "donutpass";
    private Connection con;
    private boolean isUp;

    /**
     * Constructs a new PSQL database provider.
     *
     * @param url the JDBC connection url to use
     */
    public PostgresqlLocationDb(String url) {

        isUp = true;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isUp = false;
        }

        Connection tempcon;
        try {
            tempcon = DriverManager.getConnection(url, USER, PASS);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            isUp = false;
            tempcon = null;
        }
        con = tempcon;
    }

    /**
     * Gets a connection to the database.
     * @return a connection to the database
     */
    public Connection getConnection() {
        return con;
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
    public List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type) {
        //TODO
        return null;
    }

    @Override
    public void close() {
        try {
            con.close();
        } catch (SQLException e) {
            LoggingUtils.logError(e);
        }
    }
}
