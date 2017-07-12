package org.tymit.projectdonut.stations.transitland;

import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.gtfs.GtfsProvider;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.utils.LocationUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by ilan on 6/15/17.
 */
public class TransitlandZipDb implements StationDbInstance.ComboDb {

    private TransitlandApiDb delegate = new TransitlandApiDb();

    @Override
    public List<TransStation> queryStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain) {
        if (chain != null) return delegate.queryStations(center, range, start, maxDelta, chain);
        List<URL> allChains = delegate.getFeedsInArea(center, range, null, null);
        List<TransStation> rval = new ArrayList<>();
        Predicate<TransStation> rangeFilter = withinRange(center, range);
        Predicate<TransStation> timeFIlter = withinTime(start, maxDelta);
        for (URL url : allChains) {
            GtfsProvider prov = new GtfsProvider(url);
            for (List<TransStation> stats : prov.getUpdatedStations().values()) {
                stats.stream()
                        .filter(rangeFilter)
                        .filter(timeFIlter)
                        .forEach(rval::add);
            }
        }
        return rval;
    }


    private static Predicate<TransStation> withinRange(double[] center, double range) {
        if (center == null || range < 0) return any -> true;
        return stat -> LocationUtils.distanceBetween(center, stat.getCoordinates(), true) <= range;
    }

    private static Predicate<TransStation> withinTime(TimePoint startTime, TimeDelta maxDelta) {
        if (startTime == null || maxDelta == null || startTime.equals(TimePoint.NULL) || maxDelta.getDeltaLong() <= 0) {
            return a -> true;
        }
        return station ->
                startTime.timeUntil(station.getNextArrival(startTime)).getDeltaLong() <= maxDelta.getDeltaLong();
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        return delegate.putStations(stations);
    }

    @Override
    public boolean isUp() {
        return delegate.isUp();
    }

    @Override
    public void close() {
        delegate.close();
    }
}