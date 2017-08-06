package com.reroute.backend.stations.gtfs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.stations.interfaces.StationProvider;
import com.reroute.backend.utils.LoggingUtils;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
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

    private File zipFile;
    private String source;
    private Map<TransChain, List<TransStation>> cache;
    private boolean isWorking = true;
    private boolean deleteOnCache = false;

    public GtfsProvider(String fileName) {
        this(new File(fileName));
    }

    public GtfsProvider(File file) {
        zipFile = file;
        source = file.getName();
    }

    public GtfsProvider(URL url) {
        source = url.toString();
        zipFile = new File(url.getFile().replaceAll("/", "__"));
        try {
            zipFile.delete();
            zipFile.createNewFile();
            zipFile.setWritable(true);
            URLConnection con = url.openConnection();
            URL trurl = con.getHeaderField("Location") == null
                    ? url
                    : new URL(con.getHeaderField("Location"));
            Files.copy(trurl.openStream(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            deleteOnCache = true;
        } catch (Exception e) {
            LoggingUtils.logError(e);
            isWorking = false;
        }
    }

    private static void disableApacheLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);
    }

    public String getSource() {
        return source;
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
        if (cache == null) {
            LoggingUtils.logError(new Exception("Cacheing failed."));
            isWorking = false;
            return Collections.emptyMap();
        }
        return cache;
    }

    @Override
    public void close() {
        cache.clear();
        cache = null;
    }

    private void cacheData() {
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
        Map<String, Map<TransStation, List<SchedulePoint>>> stations = GtfsSupport
                .getSchedulesForTrips(store);
        chains.keySet().forEach(tripId -> {
            TransChain chain = chains.get(tripId);
            cache.putIfAbsent(chain, new ArrayList<>());

            Set<TransStation> stationsInChain = stations.get(tripId).keySet().stream()
                    .map(station -> station.withSchedule(chain, stations.get(tripId).get(station)))
                    .collect(Collectors.toSet());

            cache.put(chain, new ArrayList<>(stationsInChain));
        });

        if (cache.size() == 0) {
            LoggingUtils.logError(getClass().getName(),
                    "WARNING: GTFS file %s returned no data. Are you sure everything is correct?",
                    zipFile.getName()
            );
        }

        if (deleteOnCache) zipFile.delete();
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
        return zipFile != null ? zipFile.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GtfsProvider provider = (GtfsProvider) o;

        return zipFile != null ? zipFile.equals(provider.zipFile) : provider.zipFile == null;

    }

    @Override
    public String toString() {
        return "GtfsProvider{" +
                "zipFileName='" + zipFile.getName() + '\'' +
                '}';
    }
}
