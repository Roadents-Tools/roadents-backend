package com.reroute.backend.stations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {

    void close();

    interface DonutCache extends StationCacheInstance {

        List<TransStation> getStationsInArea(LocationPoint center, Distance range);

        Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station);

        default Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station, TimePoint startTime, TimeDelta maxDelta) {
            return getChainsForStation(station).entrySet().stream()
                    .filter(e -> e.getValue()
                            .stream()
                            .anyMatch(pt -> startTime.timeUntil(pt.nextValidTime(startTime))
                                    .getDeltaLong() < maxDelta.getDeltaLong()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta);

        boolean putArea(LocationPoint center, Distance range, List<TransStation> stations);

        boolean putChainsForStations(TransStation station, Map<TransChain, List<SchedulePoint>> chains);

        boolean putWorld(WorldInfo request, Map<TransChain, Map<TransStation, List<SchedulePoint>>> world);

        boolean hasWorld(WorldInfo toCheck);
    }
}
