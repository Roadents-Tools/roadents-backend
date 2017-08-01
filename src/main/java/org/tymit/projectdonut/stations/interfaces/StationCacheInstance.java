package org.tymit.projectdonut.stations.interfaces;

import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.utils.StreamUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {

    interface GeneralCache extends StationCacheInstance {

        List<TransStation> getCachedStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain);

        boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations);
    }


    interface DonutCache extends StationCacheInstance {


        default Map<LocationPoint, List<TransStation>> getStationsInArea(Map<LocationPoint, Distance> ranges) {
            return ranges.entrySet().stream()
                    .collect(StreamUtils.collectWithMapping(
                            Map.Entry::getKey,
                            entry -> getStationsInArea(entry.getKey(), entry.getValue())
                    ));
        }

        List<TransStation> getStationsInArea(LocationPoint center, Distance range);

        Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station);

        default Map<TransStation, Map<TransChain, List<SchedulePoint>>> getChainsForStations(List<TransStation> stations) {
            return stations.stream()
                    .collect(StreamUtils.collectWithValues(this::getChainsForStation));
        }

        default Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(Map<TransChain, TimeDelta> chainsAndExtras, TimePoint generalStart, TimeDelta maxDelta) {
            List<TransChain> chains = new ArrayList<>(chainsAndExtras.keySet());
            Map<TransChain, Map<TransStation, TimeDelta>> raw = getArrivableStations(chains, generalStart, maxDelta);

            raw.forEach((chain, stations) -> {
                TimeDelta alreadyCounted = chainsAndExtras.get(chain);
                stations.replaceAll(
                        (station, timeDelta) -> timeDelta.minus(alreadyCounted).getDeltaLong() > 0
                                ? timeDelta.minus(alreadyCounted)
                                : null
                );
            });

            return raw;
        }

        default Map<TransChain, Map<TransStation, TimeDelta>> getArrivableStations(List<TransChain> chains, TimePoint startTime, TimeDelta maxDelta) {
            return chains.stream()
                    .collect(StreamUtils.collectWithValues(chain -> getArrivableStations(chain, startTime, maxDelta)));
        }

        Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta);

        default boolean putAreas(Map<LocationPoint, Distance> ranges, Map<LocationPoint, List<TransStation>> stations) {
            return ranges.entrySet().stream()
                    .allMatch(pt -> putArea(pt.getKey(), pt.getValue(), stations.get(pt.getKey())));
        }

        boolean putArea(LocationPoint center, Distance range, List<TransStation> stations);

        default boolean putChainsForStations(Map<TransStation, Map<TransChain, List<SchedulePoint>>> stationsAndChains) {
            return stationsAndChains.entrySet().stream()
                    .allMatch(entry -> putChainsForStations(entry.getKey(), entry.getValue()));
        }

        boolean putChainsForStations(TransStation station, Map<TransChain, List<SchedulePoint>> chains);

        default Set<LocationPoint> checkAreas(Map<LocationPoint, Distance> ranges) {
            return ranges.entrySet().stream()
                    .filter(entry -> hasArea(entry.getKey(), entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        default boolean hasArea(LocationPoint center, Distance range) {
            return !getStationsInArea(center, range).isEmpty();
        }

        boolean putWorld(Map<TransChain, Map<TransStation, List<SchedulePoint>>> world);
    }




    void close();
}
