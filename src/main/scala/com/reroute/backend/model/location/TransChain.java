package com.reroute.backend.model.location;

import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.database.DatabaseObject;

/**
 * A path taken by a public transit service. Analogous to GTFS "trips".
 * Created by ilan on 7/7/16.
 */
public class TransChain implements DatabaseObject {

    private final String name;
    private final DatabaseID id;

    /**
     * Constructs a new TransChain.
     *
     * @param name the name of the chain
     */
    public TransChain(String name) {
        this.name = name;
        this.id = null;
    }

    /**
     * Constructs a new TransChain.
     * @param name the name of the chain
     * @param id the database id of the chain
     */
    public TransChain(String name, DatabaseID id) {
        this.name = name;
        this.id = id;
    }

    /**
     * Gets the name of the chain.
     * @return the name of the chain
     */
    public String getName() {
        return name;
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
