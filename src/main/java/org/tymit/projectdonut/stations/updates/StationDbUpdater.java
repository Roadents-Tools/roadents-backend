package org.tymit.projectdonut.stations.updates;

import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.stations.database.StationDbHelper;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ilan on 7/20/16.
 */
public class StationDbUpdater {

    private static StationDbUpdater instance = new StationDbUpdater();
    private static boolean isTest = false;
    private StationProvider[] providers;
    private AtomicLong lastUpdated = new AtomicLong();
    private Thread backgroundUpdateThread;
    private AtomicLong backgroundUpdateMilli = new AtomicLong(-1);


    private StationDbUpdater() {
        initializeProviders();
    }

    private void initializeProviders() {
        if (isTest) {
            providers = new StationProvider[]{new TestStationProvider()};
            return;
        }
        providers = new StationProvider[GtfsProvider.GTFS_ZIPS.length];
        for (int i = 0; i < providers.length; i++) {
            providers[i] = new GtfsProvider(GtfsProvider.GTFS_ZIPS[i]);
        }
    }

    public static StationDbUpdater getUpdater() {
        return instance;
    }

    public static void setTestMode(boolean testMode) {
        isTest = testMode;
        instance.initializeProviders();
    }

    public void setBackgroundInterval(long intervalMilli) {
        backgroundUpdateMilli.set(intervalMilli);
        if (intervalMilli <= 0) {
            backgroundUpdateThread = null; // The old thread will stop itself and get GCed
            return;
        }
        if (backgroundUpdateThread == null) {
            backgroundUpdateThread = createBackgroundUpdateThread();
            backgroundUpdateThread.start();
        }
    }

    private Thread createBackgroundUpdateThread() {
        return new Thread(() -> {
            while (backgroundUpdateMilli.get() > 0) {
                updateStations();
                while (TimeModel.now().getUnixTime() - lastUpdated.get() < backgroundUpdateMilli.get()) {
                    try {
                        Thread.sleep(backgroundUpdateMilli.get() / 10);
                    } catch (InterruptedException e) {
                        LoggingUtils.logError(e);
                        backgroundUpdateMilli.set(-1);
                        return;
                    }
                }
            }
        });
    }

    private synchronized boolean updateStations() {

        Map<TransChain, Set<TransStation>> toPut = getUpdatedStations();

        //Our static resources should always provide at least some stations.
        if (toPut.size() == 0) {
            LoggingUtils.logError(StationDbUpdater.class.getName(), "Could not get updated stations.");
            return false;
        }

        boolean success = toPut.values().parallelStream()
                .map(stationSet -> new ArrayList<>(stationSet))
                .map(stationList -> StationDbHelper.getHelper().putStations(stationList))
                .allMatch(aBoolean -> aBoolean);

        if (success) {
            lastUpdated.set(TimeModel.now().getUnixTime());
        }

        if (!success) {
            LoggingUtils.logError(StationDbUpdater.class.getName(), "Station update failed. Please check for partially updated data.");
        }

        return success;

    }

    private Map<TransChain, Set<TransStation>> getUpdatedStations() {
        Map<TransChain, Set<TransStation>> fullMap = new ConcurrentHashMap<>();
        for (StationProvider provider : providers) {
            if (!provider.isUp()) continue;
            Map<TransChain, List<TransStation>> provMap = provider.getUpdatedStations();
            provMap.keySet().parallelStream()
                    .filter(chain -> !fullMap.containsKey(chain) || provider.updatesData())
                    .forEach(chain -> fullMap.put(chain, new HashSet<>(provMap.get(chain))));
        }
        return fullMap;
    }

    public boolean updateStationsSync() {
        return updateStations();
    }

    public CompletableFuture<Boolean> updateStationsAsync() {
        return CompletableFuture.supplyAsync(() -> updateStations());
    }


}
