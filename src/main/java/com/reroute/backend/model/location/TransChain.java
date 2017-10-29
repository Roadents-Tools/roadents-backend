package com.reroute.backend.model.location;

import com.google.common.collect.Sets;
import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.database.DatabaseObject;

import java.util.Arrays;
import java.util.Set;

/**
 * A path taken by a public transit service. Analogous to GTFS "trips".
 * Created by ilan on 7/7/16.
 */
public class TransChain implements DatabaseObject {

    private final String name;
    private final Set<TransStation> stations;
    private final DatabaseID id;

    /**
     * Constructs a new TransChain.
     *
     * @param name the name of the chain
     */
    public TransChain(String name) {
        this.name = name;
        this.stations = Sets.newConcurrentHashSet();
        this.id = null;
    }

    /**
     * Constructs a new TransChain.
     * @param name the name of the chain
     * @param id the database id of the chain
     */
    public TransChain(String name, DatabaseID id) {
        this.name = name;
        this.stations = Sets.newConcurrentHashSet();
        this.id = id;
    }

    /**
     * Gets the name of the chain.
     * @return the name of the chain
     */
    public String getName() {
        return name;
    }

    @Deprecated
    public TransStation getSchedule(TransStation base) {
        if (this.equals(base.getChain())
                && base.getSchedule() != null
                && base.getSchedule().size() > 0) {
            return base;
        }
        return stations.stream()
                .filter(st -> Arrays.equals(st.getCoordinates(), base.getCoordinates()))
                .findAny()
                .map(onto -> base.withSchedule(this, onto.getSchedule()))
                .orElse(base);
    }

    @Deprecated
    public boolean containsStation(TransStation station) {
        return containsLocation(station);
    }

    @Deprecated
    public boolean containsLocation(LocationPoint point) {
        return containsLocation(point.getCoordinates());
    }

    @Deprecated
    public boolean containsLocation(double[] coords) {
        return getStations().stream().anyMatch(st -> Arrays.equals(st.getCoordinates(), coords));
    }

    @Deprecated
    public Set<TransStation> getStations() {
        return stations;
    }

    @Deprecated
    public void addStation(TransStation station) {
        if (station != null && (!stations.contains(station) || (station.getSchedule() != null && station.getSchedule()
                .size() != 0))) {
            stations.add(station);
        }
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransChain that = (TransChain) o;

        return name != null ? name.equals(that.name) : that.name == null;

    }

    @Override
    public String toString() {
        return "TransChain{" +
                "name='" + name + '\'' +
                ", id=" + id +
                '}';
    }

    @Override
    public DatabaseID getID() {
        return id;
    }
}
