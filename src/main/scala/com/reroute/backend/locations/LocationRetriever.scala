package com.reroute.backend.locations

import com.reroute.backend.locations.helpers.LocationProviderManager
import com.reroute.backend.model.location.ReturnedLocation

/**
 * A series of static methods representing the API to query for destination locations.
 */
object LocationRetriever {
  def getLocations(reqs: Seq[LocationsRequest]): Map[LocationsRequest, Seq[ReturnedLocation]] = {
    LocationProviderManager.getLocations(reqs)
  }
}