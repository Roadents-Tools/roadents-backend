package org.tymit.projectdonut.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class TransStation implements LocationPoint {

    private static final LocationType type = new LocationType("Station", "TransStation");
    private final double[] location;
    private final String name;
    private List<TimeModel> schedule;
    private TransChain chain;


    public TransStation(String name, double[] location) {
        this.name = name;
        this.location = location;
    }

    public TransStation(String name, double[] location, List<TimeModel> schedule, TransChain chain) {
        this.location = location;
        this.name = name;
        this.schedule = schedule;
        this.chain = chain;

        chain.addStation(this);
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
        return new ArrayList<>(schedule);
    }

    public TimeModel getNextArrival(TimeModel start) {
        long min = Long.MAX_VALUE;
        TimeModel minTime = null;
        for (TimeModel possible : schedule) {
            long compareVal = possible.compareTo(start);
            if (compareVal < 0 && possible.get(TimeModel.HOUR) < 0) {
                compareVal = possible.set(TimeModel.HOUR, start.get(TimeModel.HOUR) + 1).compareTo(start);
            }
            if (compareVal < 0 && possible.get(TimeModel.DAY_OF_MONTH) < 0) {
                compareVal = possible.set(TimeModel.DAY_OF_MONTH, start.get(TimeModel.DAY_OF_MONTH) + 1).compareTo(start);
            }
            if (compareVal > 0 && compareVal < min) {
                min = compareVal;
                minTime = possible;
            }
        }
        TimeModel rval = minTime.toInstant(start);
        return rval;
    }

    public TransStation clone(List<TimeModel> newSchedule, TransChain newChain) {
        return new TransStation(name, location, newSchedule, newChain);
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

    public TransStation clone() {
        return new TransStation(name, location, schedule, chain);
    }

    @Override
    public String toString() {
        return "TransStation{" +
                "chain=" + ((chain != null) ? chain.toString() : "null") +
                ", name='" + name + '\'' +
                ", location=" + Arrays.toString(location) +
                '}';
    }
}
