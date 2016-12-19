package org.tymit.projectdonut.stations.updates;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.LoggerFactory;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/20/16.
 */
public class GtfsProvider implements StationProvider {

    private static final String DIRECTORY_BASE = "GtfsData/";

    public static final String[] GTFS_ZIPS = new String[]{
            DIRECTORY_BASE + "Vta.zip",
            DIRECTORY_BASE + "NycSubway.zip",
            DIRECTORY_BASE + "NycBusManhattan.zip",
            DIRECTORY_BASE + "NysBusBrooklyn.zip",
            DIRECTORY_BASE + "NysBusBronx.zip",
            DIRECTORY_BASE + "NysBusStatenIsland.zip",
            DIRECTORY_BASE + "NycBusCompany.zip",
            DIRECTORY_BASE + "NycBusQueens.zip",
            DIRECTORY_BASE + "MetroNorthRailroad.zip",
            DIRECTORY_BASE + "LongIslandRailroad.zip"
    };

    static {
        disableApacheLogging();
    }

    private String zipFileName;
    private Map<TransChain, List<TransStation>> cache;
    private boolean isWorking = true;

    public GtfsProvider(String fileName) {
        zipFileName = fileName;
    }

    public GtfsProvider(File file) {
        zipFileName = file.getName();
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

    @Override
    public boolean close() {
        cache.clear();
        cache = null;
        return true;
    }

    private void cacheData() {
        File zipFile = new File(zipFileName);

        GtfsDaoImpl store;

        try {
            store = readData(zipFile);
        } catch (IOException e) {
            LoggingUtils.logError(e);
            isWorking = false;
            return;
        }

        cache = new ConcurrentHashMap<>();

        Map<String, TransChain> chains = GtfsSupport.getChainsFromTrips(store);
        Map<String, Map<TransStation, List<TimeModel>>> stations = GtfsSupport.getSchedulesForTrips(store);
        chains.keySet().forEach(tripId -> {
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

    private GtfsDaoImpl readData(File file) throws IOException {
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(file);
        GtfsDaoImpl store = new GtfsDaoImpl();
        reader.setEntityStore(store);
        reader.run();
        return store;
    }

    @Override
    public int hashCode() {
        return zipFileName != null ? zipFileName.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GtfsProvider provider = (GtfsProvider) o;

        return zipFileName != null ? zipFileName.equals(provider.zipFileName) : provider.zipFileName == null;

    }

    @Override
    public String toString() {
        return "GtfsProvider{" +
                "zipFileName='" + zipFileName + '\'' +
                '}';
    }
}
