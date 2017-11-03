package com.reroute.backend.model.database;

/**
 * A unique ID for an object in a database.
 */
public class DatabaseID {

    private final String databaseName;
    private final String id;

    /**
     * Constructs a new DatabaseID object.
     *
     * @param databaseName the name of the database the object is in
     * @param id           the ID that this object represents, unique within the database
     */
    public DatabaseID(String databaseName, String id) {
        this.databaseName = databaseName;
        this.id = id;
    }

    @Override
    public String toString() {
        return "DatabaseID{" +
                "databaseName='" + databaseName + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        int result = getDatabaseName().hashCode();
        result = 31 * result + getId().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DatabaseID that = (DatabaseID) o;

        if (!getDatabaseName().equals(that.getDatabaseName())) return false;
        return getId().equals(that.getId());
    }

    /**
     * Gets the name of the database that the ID belongs to.
     * @return the name of the database the ID is in
     */
    public String getDatabaseName() {

        return databaseName;
    }

    /**
     * Gets the ID of this object.
     * @return the id of this object
     */
    public String getId() {
        return id;
    }
}
