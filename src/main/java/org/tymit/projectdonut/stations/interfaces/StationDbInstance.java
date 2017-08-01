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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();

    interface ComboDb extends StationDbInstance {

        default List<TransStation> queryStrippedStations(LocationPoint center, Distance range, int limit) {
            if (center == null || range == null || limit == 0) return Collections.emptyList();
            if (limit < 0) return queryStations(center, range, null, null, null);
            return queryStations(center, range, null, null, null).stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        List<TransStation> queryStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain);
    }

    interface DonutDb extends StationDbInstance {

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

        Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(LocationPoint center, TimePoint startTime, TimeDelta maxDelta);

        String getSourceName();
    }
}


