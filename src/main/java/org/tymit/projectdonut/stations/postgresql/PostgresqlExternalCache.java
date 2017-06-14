package org.tymit.projectdonut.stations.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationCacheInstance;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by ilan on 3/31/17.
 */
public class PostgresqlExternalCache implements StationCacheInstance {

    public static final String[] DB_URLS = new String[] { "jdbc:postgresql://donutdb.c3ovzbdvtevz.us-west-2.rds.amazonaws.com:5432/Donut" };
    private static final String USER = "donut";
    private static final String PASS = "donutpass";
    private HikariDataSource connSource;
    private boolean isUp;

    public PostgresqlExternalCache(String url) {

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
        config.setMaximumPoolSize(10);
        return config;
    }

    public boolean storeStations(Collection<? extends TransStation> stations) throws SQLException {
        double[] center = new double[] { 0, 0 };
        double range = -1;
        int size = 0;

        for (TransStation stat : stations) {
            center[0] += stat.getCoordinates()[0];
            center[1] += stat.getCoordinates()[1];
            size++;
        }

        center[0] = center[0] / size;
        center[1] = center[1] / size;

        for (TransStation stat : stations) {
            double curange = LocationUtils.distanceBetween(center, stat.getCoordinates(), true);
            if (curange > range) range = curange;
        }
        return cacheStations(center, range, TimePoint.NULL, new TimeDelta(Long.MAX_VALUE), new ArrayList<>(stations));
    }

    @Override
    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {
        try {

            //Null time values = all possible available schedule points.
            if (startTime == null && maxDelta == null) {
                startTime = TimePoint.NULL;
                maxDelta = new TimeDelta(Long.MAX_VALUE);
            }
            return PostgresSqlSupport.storeArea(this::getConnection, center, range, startTime, maxDelta, stations);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            return false;
        }
    }

    @Override
    public List<TransStation> getCachedStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        try {
            return PostgresSqlSupport.getInformation(this::getConnection, center, range, startTime, maxDelta, chain);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyList();
        }
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

    public boolean isUp() {
        return isUp;
    }

    public void close() {
        connSource.close();
    }
}
