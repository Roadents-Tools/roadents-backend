package org.tymit.projectdonut.stations.updates;

import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;

import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/20/16.
 */
public interface StationProvider {

    boolean isUp();

    boolean updatesData();

    Map<TransChain, List<TransStation>> getUpdatedStations();

    boolean close();
}
