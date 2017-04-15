package org.tymit.projectdonut.stations.caches;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.tymit.projectdonut.model.TimeDelta;
import org.tymit.projectdonut.model.TimePoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
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

    public static final String[] DB_URLS = new String[] { "jdbc:postgresql://127.0.0.1:5432/Donut" };
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
        System.out.printf("Beginning insert of %d stations...\n", size);
        return cacheStations(center, range, new TimePoint(0, "GMT-8:00"), new TimeDelta(Long.MAX_VALUE), new ArrayList<>(stations));
    }

    @Override
    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {
        try {
            return PostgresSqlSupport.storeArea(getConnection(), center, range, startTime, maxDelta, stations);
        } catch (SQLException e) {
            LoggingUtils.logError(e);
            return false;
        }
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
    public List<TransStation> getCachedStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {
        try {
            return PostgresSqlSupport.getInformation(getConnection(), center, range, startTime, maxDelta, chain);
        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyList();
        }
    }

    @Override
    public int getSize() {
        return 0;
    }

    public boolean isUp() {
        return isUp;
    }

    public void close() {
        connSource.close();
    }
}
