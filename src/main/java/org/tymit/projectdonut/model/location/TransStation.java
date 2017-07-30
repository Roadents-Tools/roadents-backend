package org.tymit.projectdonut.model.location;

import org.tymit.projectdonut.model.database.DatabaseID;
import org.tymit.projectdonut.model.database.DatabaseObject;
import org.tymit.projectdonut.model.time.SchedulePoint;
import org.tymit.projectdonut.model.time.TimePoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ilan on 7/7/16.
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

        chain.addStation(this);
    }

    public TransStation(String name, double[] location, List<SchedulePoint> schedule, TransChain chain, DatabaseID id) {
        this.location = location;
        this.name = name;
        this.schedule = schedule;
        this.chain = chain;
        this.id = id;

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
        return new TransStation(name, location, schedule, chain, id);
    }

    public TransStation stripSchedule() {
        return new TransStation(name, location, id);
    }

    @Override
    public DatabaseID getID() {
        return id;
    }
}
