package org.tymit.projectdonut.stations.memory;

import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationCacheInstance;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FatMemoryCache implements StationCacheInstance {

    private List<Range> ranges = new LinkedList<>();

    @Override
    public boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations) {
        Range toUse = ranges.stream()
                .filter(obj -> obj.containsRange(center, range))
                .findAny()
                .orElse(new Range(center, range));
        toUse.stations.addAll(stations);
        return true;
    }

    @Override
    public List<TransStation> getCachedStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain) {
        Predicate<TransStation> queryFilter = stationFilter(center, range, start, maxDelta, chain);
        return ranges.stream()
                .filter(obj -> obj.containsRange(center, range))
                .findAny()
                .map(Range::getStations)
                .map(stations -> stations.stream().filter(queryFilter).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public void close() {
        ranges.clear();
    }

    private static Predicate<TransStation> stationFilter(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain) {
        return station -> {
            if (chain != null && !chain.equals(station.getChain())) return false;
            if (LocationUtils.distanceBetween(center, station.getCoordinates(), false) > range) return false;
            if (start.timeUntil(station.getNextArrival(start)).getDeltaLong() > maxDelta.getDeltaLong()) return false;
            return true;
        };
    }

    protected class Range {
        public double[] center;
        public double range;
        public List<TransStation> stations;

        public Range(double[] center, double range) {
            this.center = center;
            this.range = range;
            stations = new ArrayList<>();
            ranges.add(this);
        }

        public boolean containsRange(double[] center, double range) {
            return range > LocationUtils.distanceBetween(center, this.center, false);
        }

        public List<TransStation> getStations() {
            return stations;
        }

    }
}
