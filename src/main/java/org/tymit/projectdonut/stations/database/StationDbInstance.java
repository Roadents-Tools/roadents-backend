package org.tymit.projectdonut.stations.database;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public interface StationDbInstance {
    List<TransStation> queryStations(double[] center, double range, TransChain chain);

    boolean putStations(List<TransStation> stations);

    boolean isUp();

    void close();
}
