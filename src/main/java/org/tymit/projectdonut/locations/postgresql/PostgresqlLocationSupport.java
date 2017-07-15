package org.tymit.projectdonut.locations.postgresql;

import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.location.LocationType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Created by ilan on 7/9/17.
 */
public class PostgresqlLocationSupport {

    private static final String LAT_KEY_FAKE = "latfakekey";
    private static final String LNG_KEY_FAKE = "lngfakekey";

    public static List<DestinationLocation> getInformation(Supplier<Connection> supplier,
                                                           double[] center, double range,
                                                           LocationType type
    ) throws SQLException {

        if (center == null || center.length != 2 || range < 0 || type == null) return Collections.emptyList();

        Connection con = supplier.get();
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(buildQuery(center, range, type));

        while (rs.isBeforeFirst()) rs.next();
        List<DestinationLocation> rval = new ArrayList<>();

        do {
            String name = rs.getString(PostgresqlLocationContract.LocationTable.NAME_KEY);
            double lat = rs.getDouble(LAT_KEY_FAKE);
            double lng = rs.getDouble(LNG_KEY_FAKE);
            rval.add(new DestinationLocation(name, type, new double[] { lat, lng }));
        } while (rs.next());

        rs.close();
        stm.close();
        con.close();

        return rval;
    }

    private static String buildQuery(double[] center, double range, LocationType type) {
        return String.format("SELECT %s.%s, " +
                        "ST_X(%s.%s) AS %s, " +
                        "ST_Y(%s.%s) AS %s " +
                        "FROM %s l LEFT JOIN %s t " +
                        "ON l.%s = t.%s " +
                        "AND ST_DWITHIN(l.%s, ST_POINT(%f, %f)::geography, %f) " +
                        "WHERE %s = %s",
                PostgresqlLocationContract.LocationTable.TABLE_NAME, PostgresqlLocationContract.LocationTable.NAME_KEY,
                PostgresqlLocationContract.LocationTable.TABLE_NAME, PostgresqlLocationContract.LocationTable.LOCATION_KEY,
                LAT_KEY_FAKE,
                PostgresqlLocationContract.LocationTable.TABLE_NAME, PostgresqlLocationContract.LocationTable.LOCATION_KEY,
                LNG_KEY_FAKE,
                PostgresqlLocationContract.LocationTable.TABLE_NAME, PostgresqlLocationContract.TagTable.TABLE_NAME,
                PostgresqlLocationContract.LocationTable.ID_KEY, PostgresqlLocationContract.TagTable.LOCATION_ID_KEY,
                PostgresqlLocationContract.LocationTable.LOCATION_KEY, center[0], center[1], range,
                PostgresqlLocationContract.TagTable.TAG_KEY, type.getEncodedname()
        );
    }

    public static boolean storeLocations(Supplier<Connection> congenerator,
                                         Collection<? extends DestinationLocation> dests
    ) throws SQLException {
        Connection con = congenerator.get();

        Statement stm = con.createStatement();

        //Location table insert
        StringJoiner locationjoiner = new StringJoiner(", ");
        StringJoiner typejoiner = new StringJoiner(", ");

        for (DestinationLocation dest : dests) {
            locationjoiner.add(String.format(
                    "(%s, ST_POINT(%f, %f)::geography)",
                    dest.getName(), dest.getCoordinates()[0], dest.getCoordinates()[1]
            ));

            typejoiner.add(String.format(
                    "((SELECT %s FROM %s WHERE %s=%s AND ST_DWITHIN(%s, ST_POINT(%f,%f)::geography)), %s)",
                    PostgresqlLocationContract.LocationTable.ID_KEY, PostgresqlLocationContract.LocationTable.TABLE_NAME,
                    PostgresqlLocationContract.LocationTable.NAME_KEY, dest.getName(),
                    PostgresqlLocationContract.LocationTable.LOCATION_KEY, dest.getCoordinates()[0], dest.getCoordinates()[1],
                    dest.getType().getEncodedname()
            ));
        }

        String locationquery = String.format("INSERT INTO %s (%s, %s) VALUES %s ON CONFLICT DO NOTHING",
                PostgresqlLocationContract.LocationTable.TABLE_NAME,
                PostgresqlLocationContract.LocationTable.NAME_KEY, PostgresqlLocationContract.LocationTable.LOCATION_KEY,
                locationjoiner.toString()
        );

        String typequery = String.format("INSERT INTO %s (%s, %s) VALUES %s ON CONFLICT DO NOTHING",
                PostgresqlLocationContract.TagTable.TABLE_NAME,
                PostgresqlLocationContract.TagTable.LOCATION_ID_KEY, PostgresqlLocationContract.TagTable.TAG_KEY,
                typejoiner.toString()
        );

        stm.executeUpdate(locationquery);
        stm.executeUpdate(typequery);


        stm.close();
        con.close();

        return true;
    }


}
