package org.tymit.projectdonut.stations.updates;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.LoggerFactory;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/20/16.
 */
public class GtfsProvider implements StationProvider {

    private static final String DIRECTORY_BASE = "GtfsData/";

    public static final String[] GTFS_ZIPS = new String[]{
            DIRECTORY_BASE + "VtaGtfs.zip",
            DIRECTORY_BASE + "NycGtfs.zip"
    };

    static {
        disableApacheLogging();
    }

    public Set<TransChain> multiTrips = new HashSet<>();
    private GtfsDaoImpl store;
    private String zipFileName;
    private Map<TransChain, List<TransStation>> cache;
    private boolean isWorking = true;

    public GtfsProvider(String fileName) {
        zipFileName = fileName;
        File file = new File(fileName);
        try {
            store = readData(file);
        } catch (IOException e) {
            LoggingUtils.logError(e);
            isWorking = false;
        }
    }

    private static GtfsDaoImpl readData(File file) throws IOException {
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(file);
        GtfsDaoImpl store = new GtfsDaoImpl();
        reader.setEntityStore(store);
        reader.run();
        return store;
    }

    public GtfsProvider(File file) {
        zipFileName = file.getName();
        try {
            store = readData(file);
        } catch (IOException e) {
            LoggingUtils.logError(e);
            isWorking = false;
        }
    }

    private static void disableApacheLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);
    }

    @Override
    public boolean isUp() {
        return isWorking;
    }

    @Override
    public boolean updatesData() {
        return false;
    }

    @Override
    public Map<TransChain, List<TransStation>> getUpdatedStations() {
        if (cache == null) cacheData();
        return new ConcurrentHashMap<>(cache);
    }

    private void cacheData() {
        cache = new ConcurrentHashMap<>();

        Map<String, TransChain> chains = getChainsFromTrips();
        Map<String, Map<TransStation, List<TimeModel>>> stations = getSchedulesForTrips();
        chains.keySet().stream().forEach(tripId -> {
            TransChain chain = chains.get(tripId);
            cache.putIfAbsent(chain, new ArrayList<>());

            Set<TransStation> stationsInChain = stations.get(tripId).keySet().stream()
                    .map(station -> station.clone(stations.get(tripId).get(station), chain))
                    .collect(Collectors.toSet());

            cache.put(chain, new ArrayList<>(stationsInChain));
        });

        if (cache.size() == 0) {
            LoggingUtils.logError(getClass().getName(),
                    "WARNING: GTFS file %s returned no data. Are you sure everything is correct?",
                    zipFileName
            );
        }
    }

    /**
     * Create TransChains from all the Trips in the gtfs folder. The TransChain's name will be
     * the short name of the route if the route only has one trip. If the route has multiple trips,
     * the name will be determined by adding a number to the end of the route's short name.
     *
     * @return the map of trip ids to TransChains
     */
    private Map<String, TransChain> getChainsFromTrips() {
        Map<String, TransChain> rval = new ConcurrentHashMap<>();

        Map<AgencyAndId, List<AgencyAndId>> routesToTrips = getTripsForRoutes();
        routesToTrips.keySet().stream().forEach(routeId -> {
            Route route = store.getRouteForId(routeId);
            List<AgencyAndId> trips = routesToTrips.get(routeId);
            int numTrips = trips.size();
            String name = (route.getLongName() == null) ? route.getShortName() : route.getLongName();
            if (numTrips == 1) {
                TransChain newChain = new TransChain(name);
                rval.put(trips.get(0).getId(), newChain);
                return;
            }
            for (AgencyAndId tripId : trips) {
                if (tripId == null) continue;
                TransChain newChain = new TransChain(name + " TripID:" + tripId.getId());
                rval.put(tripId.getId(), newChain);
                multiTrips.add(newChain);
            }
        });

        return rval;
    }

    /**
     * Get a map from trip id to TransStation in that trip to schedule for that TransStation.
     * We do NOT load the schedules into the TransStations themselves yet, as we have not yet
     * created any chains.
     *
     * @return a map from trip id -> TransStation in trip -> schedule for that TransStation
     */
    private Map<String, Map<TransStation, List<TimeModel>>> getSchedulesForTrips() {
        Map<String, Map<TransStation, List<TimeModel>>> rval = new ConcurrentHashMap<>();

        Map<String, TransStation> stations = getBaseStops();
        store.getAllStopTimes().parallelStream()
                .filter(stopTime -> stopTime.getDepartureTime() > 0 || stopTime.getArrivalTime() > 0)
                .forEach(stopTime -> {
                    String tripId = stopTime.getTrip().getId().getId();
                    rval.putIfAbsent(tripId, new ConcurrentHashMap<>());

                    TransStation station = stations.get(stopTime.getStop().getId().getId());
                    rval.get(tripId).putIfAbsent(station, new ArrayList<>());


                    TimeModel model = new TimeModel();
                    int secondsSinceMidnight = (stopTime.getDepartureTime() > 0) ? stopTime.getDepartureTime() : stopTime.getArrivalTime();


                    int trueSeconds = secondsSinceMidnight % 60;
                    model.set(TimeModel.SECOND, trueSeconds);

                    int trueMins = (secondsSinceMidnight / 60) % 60;
                    model.set(TimeModel.MINUTE, trueMins);

                    int trueHours = secondsSinceMidnight / 60 / 60;
                    model.set(TimeModel.HOUR, trueHours);
                    rval.get(tripId).get(station).add(model);
                });
        return rval;
    }

    private Map<AgencyAndId, List<AgencyAndId>> getTripsForRoutes() {
        Map<AgencyAndId, List<AgencyAndId>> rval = new ConcurrentHashMap<>();
        store.getAllTrips().parallelStream().forEach(trip -> {
            AgencyAndId routeId = trip.getRoute().getId();
            rval.putIfAbsent(routeId, new ArrayList<>());
            rval.get(routeId).add(trip.getId());
        });
        return rval;
    }

    private Map<String, TransStation> getBaseStops() {
        Map<String, TransStation> rval = new ConcurrentHashMap<>();
        store.getAllStops().parallelStream().forEach(stop -> {
            String name = stop.getName();
            double[] coords = new double[]{stop.getLat(), stop.getLon()};
            TransStation station = new TransStation(name, coords);

            String idString = stop.getId().getId();
            rval.put(idString, station);
        });
        return rval;
    }
}
