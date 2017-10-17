package com.reroute.backend.model.database;

public class DatabaseID {

    private final String databaseName;
    private final String id;

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

    public String getDatabaseName() {

        return databaseName;
    }

    public String getId() {
        return id;
    }
}
