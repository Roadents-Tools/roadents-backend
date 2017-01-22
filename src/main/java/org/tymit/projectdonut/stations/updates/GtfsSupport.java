package org.tymit.projectdonut.stations.updates;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.slf4j.LoggerFactory;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/29/16.
 */
public class GtfsSupport {

    static {
        disableApacheLogging();
    }

    public static void disableApacheLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);
    }

    /**
     * Get a map from trip id to TransStation in that trip to schedule for that TransStation.
     * We do NOT load the schedules into the TransStations themselves yet, as we have not yet
     * created any chains.
     *
     * @param store
     * @return a map from trip id -> TransStation in trip -> schedule for that TransStation
     */
    public static Map<String, Map<TransStation, List<TimeModel>>> getSchedulesForTrips(GtfsDaoImpl store) {
        Map<String, Map<TransStation, List<TimeModel>>> rval = new ConcurrentHashMap<>();


        Map<String, TransStation> stations = getBaseStops(store);
        store.getAllStopTimes().stream()
                .filter(stopTime -> stopTime.getDepartureTime() > 0 || stopTime.getArrivalTime() > 0)
                .forEach(stopTime -> {
                    String tripId = stopTime.getTrip().getId().getId();
                    rval.putIfAbsent(tripId, new ConcurrentHashMap<>());

                    TransStation station = stations.get(stopTime.getStop().getId().getId());
                    rval.get(tripId).putIfAbsent(station, new ArrayList<>());

                    int secondsSinceMidnight = (stopTime.getDepartureTime() > 0) ? stopTime.getDepartureTime() : stopTime.getArrivalTime();

                    TimeModel model = TimeModel.empty()
                            .set(TimeModel.SECOND, secondsSinceMidnight % 60)
                            .set(TimeModel.MINUTE, (secondsSinceMidnight / 60) % 60)
                            .set(TimeModel.HOUR, secondsSinceMidnight / 60 / 60);
                    rval.get(tripId).get(station).add(model);
                });
        return rval;
    }

    /**
     * Get all the stops in this store and convert them to ids and TransStations.
     * Note that these stations are not part of a chain, nor do they have an associated
     * schedule.
     *
     * @param store the store to read from
     * @return a map from ID to converted TransStation
     */
    public static Map<String, TransStation> getBaseStops(GtfsDaoImpl store) {
        return store.getAllStops().parallelStream()
                .collect(ConcurrentHashMap::new,
                        (rval1, stop) -> rval1.put(stop.getId().getId(),
                                new TransStation(stop.getName()
                                        .trim(), new double[] { stop.getLat(), stop.getLon() })
                        ),
                        ConcurrentHashMap::putAll
                );
    }

    /**
     * Create TransChains from all the Trips in the gtfs folder. The TransChain's name will be
     * the short name of the route if the route only has one trip. If the route has multiple trips,
     * the name will be determined by adding a number to the end of the route's short name.
     *
     * @param store
     * @return the map of trip ids to TransChains
     */
    public static Map<String, TransChain> getChainsFromTrips(GtfsDaoImpl store) {
        Map<String, TransChain> rval = new ConcurrentHashMap<>();

        Map<AgencyAndId, List<AgencyAndId>> routesToTrips = getTripsForRoutes(store);
        routesToTrips.keySet().forEach(routeId -> {
            Route route = store.getRouteForId(routeId);
            List<AgencyAndId> trips = routesToTrips.get(routeId);
            String name = (route.getLongName() == null) ? route.getShortName() : route.getLongName();
            if (trips.size() == 1) {
                rval.put(trips.get(0).getId(), new TransChain(name));
                return;
            }
            trips.parallelStream()
                    .filter(Objects::nonNull)
                    .forEach(tripId -> rval.put(tripId.getId(), new TransChain(name + " TripID:" + tripId.getId())));
        });

        return rval;
    }

    /**
     * Get all the trips for each route in the store.
     *
     * @param store the store to read from
     * @return a map from route ID to a list of trip IDs.
     */
    public static Map<AgencyAndId, List<AgencyAndId>> getTripsForRoutes(GtfsDaoImpl store) {
        Map<AgencyAndId, List<AgencyAndId>> rval = new ConcurrentHashMap<>();
        store.getAllTrips().forEach(trip -> {
            AgencyAndId routeId = trip.getRoute().getId();
            rval.putIfAbsent(routeId, new ArrayList<>());
            rval.get(routeId).add(trip.getId());
        });
        return rval;
    }
}
