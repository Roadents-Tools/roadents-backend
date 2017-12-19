package com.reroute.backend.locations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.List;

/**
 * A source of locations. This is considered to be full and complete for all queries that it accepts.
 */
public interface LocationProvider {

    /**
     * Checks whether or not we can currently use this provider.
     *
     * @return whether this provider is usable
     */
    boolean isUsable();

    /**
     * Checks whether or not we can use this provider for a given type.
     * @param type the type to check
     * @return whether we can give valid results for that type
     */
    boolean isValidType(LocationType type);

    /**
     * Queries for locations in the provider.
     * @param center the center of the query
     * @param range the distance around the center to check
     * @param type the type of destination to get
     * @return the locations that meet this query
     */
    List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type);

    /**
     * Shuts down the cache. If this cache is a network resource, closes the connection. If this cache is an in-memory
     * map, destroys the map. Etc.
     */
    void close();
}
