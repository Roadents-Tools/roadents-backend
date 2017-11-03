package com.reroute.backend.model.database;

/**
 * An object that can be stored and retrieved from some sort of database.
 */
public interface DatabaseObject {

    /**
     * Gets the ID of this object.
     *
     * @return the ID of this object
     */
    DatabaseID getID();
}
