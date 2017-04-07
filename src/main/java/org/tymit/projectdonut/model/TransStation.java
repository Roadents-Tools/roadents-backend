package org.tymit.projectdonut.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
 */
public class TransStation implements LocationPoint {

    private static final LocationType type = new LocationType("Station", "TransStation");
    private final double[] location;
    private final String name;
    private final List<SchedulePoint> schedule;
    private final TransChain chain;


    public TransStation(String name, double[] location) {
        this.name = name;
        this.location = location;
        schedule = null;
        chain = null;
    }

    public TransStation(String name, double[] location, List<SchedulePoint> schedule, TransChain chain) {
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

    public List<SchedulePoint> getSchedule() {
        return new ArrayList<>(schedule);
    }

    public TimePoint getNextArrival(TimePoint start) {
        TimePoint tp = schedule.parallelStream()
                .map(sp -> sp.nextValidTime(start))
                .min(Comparator.naturalOrder())
                .get();
        return tp;
    }

    public TransStation clone(List<SchedulePoint> newSchedule, TransChain newChain) {
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
        return chain != null ? chain.equals(that.chain) : that.chain == null;

    }

    public TransStation clone() {
        return new TransStation(name, location, schedule, chain);
    }

    @Override
    public String toString() {
        return "TransStation{" +
                "location=" + Arrays.toString(location) +
                ", name='" + name + '\'' +
                ", schedule=" + schedule +
                ", chain=" + chain +
                '}';
    }

    public TransStation withSchedule(TransChain chain, List<SchedulePoint> schedule) {
        return new TransStation(name, location, schedule, chain);
    }

    public TransStation stripSchedule() {
        return new TransStation(name, location);
    }
}
