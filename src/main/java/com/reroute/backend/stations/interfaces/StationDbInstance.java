package com.reroute.backend.stations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;

import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();

    interface ComboDb extends StationDbInstance {

        List<TransStation> queryStations(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta, TransChain chain);
    }

    interface DonutDb extends StationDbInstance {

        List<TransStation> getStationsInArea(LocationPoint center, Distance range);

        Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station);

        Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta);

        Map<TransChain, Map<TransStation, List<SchedulePoint>>> getWorld(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta);

        String getSourceName();
    }
}


