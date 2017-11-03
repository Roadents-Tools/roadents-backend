package com.reroute.backend.locations.interfaces;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;

import java.util.List;

/**
 * An entity to cache locations.
 * This implies that
 * A) this entity is faster that a provider, and
 * B) this entity does not necessarily contain all information.
 * As such, information can be inserted programmatically as necessary.
 */
public interface LocationCacheInstance {

    /**
     * Adds a new query to the cache.
     *
     * @param center    the center of the area
     * @param range     the distance around the center we are adding
     * @param type      the type of destination of this query
     * @param locations the locations to cache for this query
     */
    void cacheLocations(LocationPoint center, Distance range, LocationType type, List<DestinationLocation> locations);

    /**
     * Gets the locations cached in this cache, or null if the query has not yet been cached.
     * @param center the center of the query
     * @param range the distance around center to get
     * @param type the type of destination to get
     * @return the cached locations, or null if this cache does not have enough information to return correctly
     */
    List<DestinationLocation> getCachedLocations(LocationPoint center, Distance range, LocationType type);

    /**
     * Gets the space that this cache takes up, in relative units usually calculated to be the number of total
     * locations in the cache.
     * @return the size of the cache
     */
    int getSize();

    /**
     * Clears the cache of all entries.
     */
    void clear();

    /**
     * Shuts down the cache. If this cache is a network resource, closes the connection. If this cache is an in-memory
     * map, destroys the map. Etc.
     */
    void close();
}
