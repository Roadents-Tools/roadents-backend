package org.tymit.projectdonut.model;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Created by ilan on 7/7/16.
 */
public class TransChain {

    private final String name;
    private final Set<TransStation> stations;

    public TransChain(String name) {
        this.name = name;
        this.stations = Sets.newConcurrentHashSet();
    }

    public String getName() {
        return name;
    }

    public Set<TransStation> getStations() {
        return stations;
    }

    public void addStation(TransStation station) {
        if (station != null && (!stations.contains(station) || (station.getSchedule() != null && station
                .getSchedule()
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
                '}';
    }
}
