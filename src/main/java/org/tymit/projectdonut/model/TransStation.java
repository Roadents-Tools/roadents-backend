package org.tymit.projectdonut.model;

import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class TransStation implements LocationPoint {

    private double[] location;
    private LocationType type = new LocationType("Station", "TransStation");
    private String name;
    private List<TimeModel> schedule;

    public TransStation(double[] location, LocationType type, String name, List<TimeModel> schedule) {
        this.location = location;
        this.type = type;
        this.name = name;
        this.schedule = schedule;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LocationType getType() {
        return type;
    }

    @Override
    public double[] getCoordinates() {
        return location;
    }

    public List<TimeModel> getSchedule() {
        return schedule;
    }

    public TimeModel getNextArrival(TimeModel start) {
        int min = Integer.MAX_VALUE;
        TimeModel minTime = null;
        for (TimeModel possible : schedule) {
            int compareVal = start.compareTo(possible);
            if (compareVal > 0 && compareVal < min) {
                min = compareVal;
                minTime = possible;
            }
        }
        return minTime;
    }
}
