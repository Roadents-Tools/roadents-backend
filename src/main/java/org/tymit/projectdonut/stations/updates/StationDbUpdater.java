package org.tymit.projectdonut.stations.updates;

import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.stations.database.StationDbHelper;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/20/16.
 */
public class StationDbUpdater {

    private static StationDbUpdater instance = new StationDbUpdater();
    private static boolean isTest = false;
    private final AtomicLong lastUpdated = new AtomicLong(-1);
    private final AtomicLong backgroundUpdateMilli = new AtomicLong(-1);
    private StationProvider[] providers;
    private Thread backgroundUpdateThread;


    private StationDbUpdater() {
        initializeProviders();
    }

    private void initializeProviders() {
        if (isTest) {
            providers = new StationProvider[]{new TestStationProvider()};
        } else {
            providers = new StationProvider[GtfsProvider.GTFS_ZIPS.length];
            for (int i = 0; i < providers.length; i++) {
                providers[i] = new GtfsProvider(GtfsProvider.GTFS_ZIPS[i]);
            }
        }
    }

    public static StationDbUpdater getUpdater() {
        return instance;
    }

    public static void setTestMode(boolean testMode) {
        isTest = testMode;
        instance = new StationDbUpdater();
    }

    public long getLastUpdated() {
        return lastUpdated.get();
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
                while (System.currentTimeMillis() - lastUpdated.get() < backgroundUpdateMilli
                        .get()) {
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

        LoggingUtils.logMessage(getClass().getName(), "Updating databases from %d providers.", providers.length);


        //Sort into static and dynamic
        List<StationProvider> nonUpdating = new ArrayList<>();
        List<StationProvider> updating = new ArrayList<>();

        for (StationProvider provider : providers) {
            if (!provider.isUp()) continue;
            if (!provider.updatesData()) {
                nonUpdating.add(provider);
                continue;
            }
            updating.add(provider);
        }

        if (nonUpdating.isEmpty() && updating.isEmpty()) return false;

        boolean isSuccessful = true;

        //Static data goes first, so that dynamic can override
        for (StationProvider non : nonUpdating) {
            LoggingUtils.logMessage(getClass().getName(), "Loading from " + non.toString());
            List<TransStation> oldData = non.getUpdatedStations()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            LoggingUtils.logMessage(getClass().getName(), "Got %d stations", oldData.size());
            boolean nonSuccess = StationDbHelper.getHelper().putStations(oldData);
            isSuccessful = nonSuccess && isSuccessful;
            non.close();
            oldData.clear();
            System.gc();
            LoggingUtils.logMessage(getClass().getName(), (nonSuccess) ? "Success!" : "Fail");
        }

        for (StationProvider upd : updating) {
            LoggingUtils.logMessage(getClass().getName(), "Loading from " + upd.toString());
            List<TransStation> newData = upd.getUpdatedStations()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            boolean updSuccess = StationDbHelper.getHelper().putStations(newData);
            isSuccessful = updSuccess && isSuccessful;
            upd.close();
            newData.clear();
            System.gc();
            LoggingUtils.logMessage(getClass().getName(), (updSuccess) ? "Success!" : "Fail");
        }

        if (isSuccessful) lastUpdated.set(System.currentTimeMillis());
        else LoggingUtils.logError(getClass().getName(), "Could not update database.");

        return isSuccessful;
    }

    public boolean updateStationsSync() {
        return updateStations();
    }

    public CompletableFuture<Boolean> updateStationsAsync() {
        return CompletableFuture.supplyAsync(this::updateStations);
    }


}
