package com.reroute.backend.stations.interfaces;

import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;

import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/20/16.
 */
public interface StationProvider {

    boolean isUp();

    boolean updatesData();

    Map<TransChain, List<TransStation>> getUpdatedStations();

    void close();
}
