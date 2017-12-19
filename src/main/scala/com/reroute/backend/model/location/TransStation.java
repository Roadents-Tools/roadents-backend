package com.reroute.backend.model.location;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.database.DatabaseObject;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimePoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A location that a TransChain can stop at.
 */
public class TransStation implements LocationPoint, DatabaseObject {

    private static final LocationType type = new LocationType("Station", "TransStation");
    private final double[] location;
    private final String name;
    private final List<SchedulePoint> schedule;
    private final TransChain chain;
    private final DatabaseID id;


    public TransStation(String name, double[] location) {
        this.name = name;
        this.location = location;
        schedule = null;
        chain = null;
        id = null;
    }

    public TransStation(String name, double[] location, DatabaseID id) {
        this.name = name;
        this.location = location;
        this.id = id;
        this.chain = null;
        this.schedule = null;
    }

    public TransStation(String name, double[] location, List<SchedulePoint> schedule, TransChain chain) {
        this.location = location;
        this.name = name;
        this.schedule = schedule;
        this.chain = chain;
        id = null;
    }

    public TransStation(String name, double[] location, List<SchedulePoint> schedule, TransChain chain, DatabaseID id) {
        this.location = location;
        this.name = name;
        this.schedule = schedule;
        this.chain = chain;
        this.id = id;
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
        return schedule == null ? null : new ArrayList<>(schedule);
    }

    public TimePoint getNextArrival(TimePoint start) {
        return schedule.parallelStream()
                .map(sp -> sp.nextValidTime(start))
                .min(Comparator.naturalOrder())
                .orElse(TimePoint.NULL);
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

    /**
     * Clones the station.
     *
     * @return a clone of this station
     */
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
                ", id=" + id +
                '}';
    }

    /**
     * Returns a version of this station with the passed schedule information.
     * @param chain the chain of the schedule
     * @param schedule the schedule itself
     * @return a station representing this with the given schedule information
     */
    public TransStation withSchedule(TransChain chain, List<SchedulePoint> schedule) {
        return new TransStation(name, location, schedule, chain, id);
    }

    /**
     * Returns a stripped schedule version of this station.
     * @return a station representing this without schedule information
     */
    public TransStation stripSchedule() {
        return new TransStation(name, location, id);
    }

    @Override
    public DatabaseID getID() {
        return id;
    }
}
