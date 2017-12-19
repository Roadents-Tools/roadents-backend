package com.reroute;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.stations.gtfs.GtfsPostgresLoader;
import com.reroute.backend.stations.transitland.TransitlandApiDb;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.displayers.restcontroller.SparkHandler;
import scala.util.Try;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ilan on 1/5/17.
 */
public class ScratchRunner {


    public static void main(String[] args) {

        LoggingUtils.setPrintImmediate(true);
        try {
            for (String arg : args) {
                if ("--spark".equals(arg)) {
                    runSpark(args);
                    return;
                }
                if ("--urls".equals(arg)) {
                    listUrls(args);
                    return;
                }
                if ("--loadNew".equals(arg)) {
                    loadZipsNew(args);
                    return;
                }
            }
            System.out.println("ARGUMENT NOT FOUND, EXITING...");
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
        apidb.getFeedsInArea(center, range, null, skipBad).forEach(System.out::println);
    }

    private static void loadZipsNew(String[] args) {
        String zipurl = null;
        String db = null;
        for (int i = 0; i < args.length; i++) {
            if ("--zip".equals(args[i])) {
                zipurl = args[i + 1];
            } else if ("--db".equals(args[i])) {
                db = args[i + 1];
            }
        }

        if (zipurl == null || db == null) {
            System.out.printf("Got null args:\ndb = %s\nzip = %s\n", db, zipurl);
            return;
        }

        URL url;
        try {
            url = new URL(zipurl);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("BAD URL: " + zipurl);
            return;
        }
        GtfsPostgresLoader loader = new GtfsPostgresLoader(url);
        Try<Object> res = loader.load(db, "donut", "donutpass");
        if (res.isFailure()) {
            System.out.printf("Got args:\ndb = %s\nzip = %s\n", db, zipurl);
            res.toEither().left().get().printStackTrace();
        }
        System.out.println("Build complete.");
    }

}

