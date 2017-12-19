package com.reroute.backend.stations.gtfs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.utils.StreamUtils;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Trip;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    public static Map<String, Map<TransStation, List<SchedulePoint>>> getSchedulesForTrips(GtfsDaoImpl store) {
        Map<String, Map<TransStation, List<SchedulePoint>>> rval = new ConcurrentHashMap<>();

        Map<String, TransStation> stations = getBaseStops(store);
        Map<String, boolean[]> serviceToSchedule = servicesToValid(store);
        store.getAllStopTimes().stream()
                .filter(stopTime -> stopTime.getDepartureTime() > 0 || stopTime.getArrivalTime() > 0)
                .forEach(stopTime -> {
                    String tripId = stopTime.getTrip().getId().getId();
                    String serviceId = stopTime.getTrip().getServiceId().getId();
                    TransStation station = stations.get(stopTime.getStop().getId().getId());
                    int secondsSinceMidnight = stopTime.getArrivalTime();

                    int fuzz = (stopTime.getDepartureTime() > 0 && stopTime.getArrivalTime() > 0)
                            ? stopTime.getDepartureTime() - stopTime.getArrivalTime()
                            : 60;

                    SchedulePoint model = new SchedulePoint(secondsSinceMidnight, serviceToSchedule.getOrDefault(serviceId, null), (long) fuzz, null);

                    rval.computeIfAbsent(tripId, tid -> new ConcurrentHashMap<>())
                            .computeIfAbsent(station, st -> new ArrayList<>())
                            .add(model);
                });
        return rval;
    }

    public static Map<String, boolean[]> servicesToValid(GtfsDaoImpl store) {
        return store.getAllCalendars().stream()
                .collect(StreamUtils.collectWithMapping(
                        cl -> cl.getServiceId().getId(),
                        cl -> new boolean[] {
                                cl.getSunday() == 1, cl.getMonday() == 1, cl.getTuesday() == 1,
                                cl.getWednesday() == 1, cl.getThursday() == 1, cl.getFriday() == 1,
                                cl.getSaturday() == 1
                        }
                ));
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
                                new TransStation(stop.getName().trim(), new double[] { stop.getLat(), stop.getLon() })
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

        Map<AgencyAndId, List<AgencyAndId>> routesToTrips = store.getAllTrips()
                .stream()
                .collect(Collectors.groupingBy(
                        trip -> trip.getRoute().getId(),
                        ConcurrentHashMap::new,
                        Collectors.mapping(Trip::getId, Collectors.toList())
                ));

        routesToTrips.forEach((key, trips) -> {
            Route route = store.getRouteForId(key);
            String name = (route.getLongName() == null) ? route.getShortName() : route.getLongName();
            trips.stream()
                    .filter(Objects::nonNull)
                    .forEach(tripId -> rval.put(tripId.getId(), new TransChain(name + " TripID:" + tripId.getId())));
        });

        return rval;
    }

}
