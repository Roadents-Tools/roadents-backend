package com.reroute.backend.stations.gtfs;

import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/29/16.
 */
public class GtfsSupportTest {

    private static final String TEST_FILE = "GtfsData/MetroNorthRailroad.zip";
    private static final int EXPECTED_BASE_STOPS = 143;
    private static final int EXPECTED_ROUTES = 11;
    private static final int EXPECTED_TRIPS = 12905;
    private static final int EXPECTED_TRIP_ROUTES = 6;
    private static final int EXPECTED_STATIONS_WITH_TRIPS = 112;
    private GtfsDaoImpl store;

    @Before
    public void setUpStore() throws IOException {

        GtfsSupport.disableApacheLogging();

        File file = new File(TEST_FILE);
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(file);
        store = new GtfsDaoImpl();
        reader.setEntityStore(store);
        reader.run();
    }

    @Test
    public void getBaseStops() throws Exception {
        Map<String, TransStation> stops = GtfsSupport.getBaseStops(store);
        Assert.assertEquals(EXPECTED_BASE_STOPS, stops.size());
    }

    @Test
    public void getSchedulesForTrips() throws Exception {
        Map<String, Map<TransStation, List<SchedulePoint>>> tripMaps = GtfsSupport
                .getSchedulesForTrips(store);
        Assert.assertEquals(EXPECTED_TRIPS, tripMaps.size());

        Set<TransStation> allStations = tripMaps.values().stream()
                .flatMap(stationMap -> stationMap.keySet().stream())
                .collect(Collectors.toSet());
        Assert.assertEquals(EXPECTED_STATIONS_WITH_TRIPS, allStations.size());
    }

    @Test
    public void getChainsFromTrips() throws Exception {
        Map<String, TransChain> chains = GtfsSupport.getChainsFromTrips(store);
        Assert.assertEquals(EXPECTED_TRIPS, chains.size());
    }

}