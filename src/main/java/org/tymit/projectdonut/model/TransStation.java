package org.tymit.projectdonut.model;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class TransStation implements LocationPoint {

    private double[] location;
    private LocationType type = new LocationType("Station", "TransStation");
    private String name;
    private List<TimeModel> schedule;
    private TransChain chain;

    public TransStation(String name, double[] location, List<TimeModel> schedule, TransChain chain) {
        this.location = location;
        this.name = name;
        this.schedule = schedule;
        this.chain = chain;
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

    public TransChain getChain() {
        return chain;
    }

    public List<TimeModel> getSchedule() {
        return schedule;
    }

    public TimeModel getNextArrival(TimeModel start) {
        long min = Long.MAX_VALUE;
        TimeModel minTime = null;
        for (TimeModel possible : schedule) {
            long compareVal = possible.compareTo(start);
            if (compareVal > 0 && compareVal < min) {
                min = compareVal;
                minTime = possible;
            }
        }
        TimeModel rval = minTime.toInstant(start);
        return rval;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(location);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (chain != null ? chain.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransStation that = (TransStation) o;

        if (!Arrays.equals(location, that.location)) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return chain != null ? chain.equals(that.chain) : that.chain == null;

    }
}
