package com.reroute.backend.stations.test;

import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.interfaces.StationProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ilan on 7/25/16.
 */
public class TestStationProvider implements StationProvider {


    private static final double LATITUDE = 37.358658;
    private static final double LONGITUDE = -122.008763;
    private static final int CHAINS = 5;
    private static final int STATIONS_PER_CHAIN = 10;
    private static final int[] HOURS = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    private static final int[] MINUTES = new int[]{15, 30, 45, 0};


    private static final double[] RANDOM_DOUBLES = new double[]{
            0.954470990216, 0.801008515453, 0.565200511583, 0.313143350741, 0.420696845601,
            0.318458246776, 0.730757852173, 0.560057610404, 0.768126111434, 0.518809366165,
            0.25947102222, 0.623833704867, 0.317469033366, 0.496390088364, 0.134104851286,
            0.745124435932, 0.311569501316, 0.68496502386, 0.980038390739, 0.237028609205,
            0.642257365681, 0.838757528477, 0.0535795833642, 0.460262470445, 0.00803651278283,
            0.493213559369, 0.13779199375, 0.382484542303, 0.111983324058, 0.691148780412,
            0.777429844316, 0.780381416451, 0.807520099518, 0.607099512043, 0.363063704319
    };

    private int rngIndex = 0;

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    public boolean updatesData() {
        return false;
    }

    @Override
    public Map<TransChain, List<TransStation>> getUpdatedStations() {
        TimePoint currentTime = new TimePoint(System.currentTimeMillis(), "America/Los_Angeles");
        String calledTime = String.format("%d:%d:%d", currentTime.getHour(), currentTime
                .getMinute(), currentTime.getSecond());
        return initStations(calledTime);
    }

    @Override
    public void close() {
    }

    private Map<TransChain, List<TransStation>> initStations(String timeCalled) {
        Map<TransChain, List<TransStation>> allStations = new HashMap<>(CHAINS);
        List<SchedulePoint> schedule = new ArrayList<>();
        for (int h : HOURS) {
            for (int m : MINUTES) {
                schedule.add(new SchedulePoint(h, m, 0, null, 60));
            }
        }
        for (int i = 0; i < CHAINS; i++) {
            TransChain newChain = new TransChain("TESTDB CHAIN " + i);
            List<TransStation> newStations = new ArrayList<>(STATIONS_PER_CHAIN);
            for (int j = 0; j < STATIONS_PER_CHAIN; j++) {
                TransStation newStation = new TransStation("TESTDB STATION " + i + "," + j + " CREATED AT " + timeCalled,
                        new double[]{LATITUDE + getRandomDouble() * (j * 10 + 0.001), LONGITUDE + getRandomDouble() * (j * 10 + 0.001)},
                        schedule, newChain);
                newStations.add(newStation);
            }
            allStations.put(newChain, newStations);
        }
        return allStations;
    }

    private double getRandomDouble() {
        rngIndex++;
        return RANDOM_DOUBLES[rngIndex % RANDOM_DOUBLES.length];
    }
}
