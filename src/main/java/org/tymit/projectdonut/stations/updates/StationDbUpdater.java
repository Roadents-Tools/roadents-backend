package org.tymit.projectdonut.stations.updates;

import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.stations.database.StationDbHelper;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

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
            List<TransStation> oldData = new ArrayList<>();
            non.getUpdatedStations().values().forEach(oldData::addAll);
            LoggingUtils.logMessage(getClass().getName(), "Got %d stations", oldData.size());
            boolean nonSuccess = StationDbHelper.getHelper().putStations(oldData);
            if (!nonSuccess) {
                isSuccessful = false;
            }
            non.close();
            oldData.clear();
            System.gc();
            LoggingUtils.logMessage(getClass().getName(), (nonSuccess) ? "Success!" : "Fail");
        }

        for (StationProvider non : updating) {
            LoggingUtils.logMessage(getClass().getName(), "Loading from " + non.toString());
            List<TransStation> newData = new ArrayList<>();
            non.getUpdatedStations().values().forEach(newData::addAll);
            boolean nonSuccess = StationDbHelper.getHelper().putStations(newData);
            if (!nonSuccess) {
                isSuccessful = false;
            }
            non.close();
            newData.clear();
            System.gc();
            LoggingUtils.logMessage(getClass().getName(), (nonSuccess) ? "Success!" : "Fail");
        }

        if (isSuccessful) lastUpdated.set(TimeModel.now().getUnixTime());
        if (!isSuccessful) {
            LoggingUtils.logError(getClass().getName(), "Could not update database.");
        }

        return isSuccessful;
    }

    public boolean updateStationsSync() {
        return updateStations();
    }

    public CompletableFuture<Boolean> updateStationsAsync() {
        return CompletableFuture.supplyAsync(this::updateStations);
    }


}
