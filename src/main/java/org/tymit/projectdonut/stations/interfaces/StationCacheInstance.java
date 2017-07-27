package org.tymit.projectdonut.stations.interfaces;

import org.tymit.projectdonut.model.location.LocationPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.List;

/**
 * Created by ilan on 8/31/16.
 */
public interface StationCacheInstance {

    interface GeneralCache extends StationCacheInstance {

        List<TransStation> getCachedStations(double[] center, double range, TimePoint start, TimeDelta maxDelta, TransChain chain);

        boolean cacheStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, List<TransStation> stations);
    }


    interface DonutCache extends StationCacheInstance {

        List<TransStation> getStationsInArea(LocationPoint center, double range);

        List<TransChain> getChainsForStation(TransStation station);

        List<TransStation> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta);


        boolean putArea(LocationPoint center, double range, List<TransStation> stations);

        boolean putChainsForStation(TransStation station, List<TransChain> chains);

        boolean putStationsForChain(TransChain chain, List<TransStation> station);
    }




    void close();
}
