package com.reroute;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.gtfs.GtfsProvider;
import com.reroute.backend.stations.postgresql.PostgresqlDonutDb;
import com.reroute.backend.stations.redis.RedisDonutCache;
import com.reroute.backend.stations.transitland.TransitlandApiDb;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.displayers.restcontroller.SparkHandler;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) {

        LoggingUtils.setPrintImmediate(true);
        try {
            for (String arg : args) {
                if ("--cache".equals(arg)) {
                    cache(args);
                    return;
                }
                if ("--spark".equals(arg)) {
                    runSpark(args);
                    return;
                }
                if ("--urls".equals(arg)) {
                    listUrls(args);
                    return;
                }
                if ("--load".equals(arg)) {
                    loadtransitzips(args);
                    return;
                }
            }
        } catch (Exception e) {
            LoggingUtils.logError(e);
        }
    }

    private static void runSpark(String[] args) {
        SparkHandler.main(args);
    }

    private static void listUrls(String[] args) {
        Distance range = new Distance(1, DistanceUnits.METERS);
        Double lat = null;
        Double lng = null;


        for (int i = 0; i < args.length; i++) {
            if ("-lat".equals(args[i]) && args.length > i + 1) {
                lat = Double.parseDouble(args[i + 1]);
            } else if ("-lng".equals(args[i]) && args.length > i + 1) {
                lng = Double.parseDouble(args[i + 1]);
            } else if ("-d".equals(args[i]) && args.length > i + 1) {
                range = new Distance(Double.parseDouble(args[i + 1]), DistanceUnits.METERS);
            }
        }

        if (lat == null || lng == null) {
            LoggingUtils.logError("ScratchRunner", "Coords not passed correctly.");
            return;
        }

        StartPoint center = new StartPoint(new double[] { lat, lng });
        TransitlandApiDb apidb = new TransitlandApiDb();
        Map<String, String> skipBad = new HashMap<>();
        skipBad.put("license_use_without_attribution", "no");
        apidb.getFeedsInArea(center, range, null, skipBad).stream()
                .peek(System.out::println)
                .forEach(url -> {
                    String result;

                    String rval = "/home/main/Downloads/tzip/" + url.getFile().replaceAll("/", "__");

                    File zipFile = new File(rval);
                    try {
                        zipFile.delete();
                        zipFile.createNewFile();
                        zipFile.setWritable(true);
                        URLConnection con = url.openConnection();
                        URL trurl = con.getHeaderField("Location") == null
                                ? url
                                : new URL(con.getHeaderField("Location"));
                        Files.copy(trurl.openStream(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        result = rval;
                    } catch (Exception e) {
                        LoggingUtils.logError(e);
                        result = null;
                    }
                });
    }

    private static void loadtransitzips(String[] args) {
        String dburl = PostgresqlDonutDb.DB_URLS[1];
        String rootdir = null;
        for (int i = 0; i < args.length; i++) {
            if ("-d".equals(args[i]) && args.length > i + 1) {
                rootdir = args[i + 1];
            }
            if ("-url".equals(args[i]) && args.length > i + 1) {
                dburl = args[i + 1];
            }
        }

        if (dburl == null || rootdir == null) {
            LoggingUtils.logError("ScratchRunner", "Got null arguments for database populator. Returning.");
            return;
        }

        PostgresqlDonutDb db = new PostgresqlDonutDb(dburl);
        File rootFile = new File(rootdir);
        Arrays.stream(rootFile.listFiles()).parallel().allMatch(file -> {
            LoggingUtils.logMessage("DB Loader", "Starting URL %s.", file.getName());
            Map<TransChain, List<TransStation>> mp = new GtfsProvider(file).getUpdatedStations();
            if (!mp.values().stream().allMatch(db::putStations)) {
                LoggingUtils.logError("DB Loader", "ERR on URL %s.", file.getName());
                return false;
            }
            LoggingUtils.logMessage("DB Loader", "Finished URL %s.", file.getName());
            return true;
        });
    }

    private static void cache(String[] args) {
        RedisDonutCache cache = new RedisDonutCache("debstop.dynamic.ucsd.edu", 6379);
        PostgresqlDonutDb db = new PostgresqlDonutDb(PostgresqlDonutDb.DB_URLS[0]);
        WorldInfo req = new WorldInfo.Builder()
                .setCenter(new StartPoint(40.676843, -74.041512))
                .setRange(new Distance(1000, DistanceUnits.KILOMETERS))
                .setStartTime(TimePoint.NULL)
                .setMaxDelta(new TimeDelta(Long.MAX_VALUE))
                .build();
        cache.putWorld(req, db.getWorld(req.getCenter(), req.getRange(), req.getStartTime(), req.getMaxDelta()));
    }

}

