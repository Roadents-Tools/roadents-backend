package com.reroute.backend.locations.interfaces

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.model.location.ReturnedLocation

trait LocationProvider {

  def queryLocations(requests: Seq[LocationsRequest]): Map[LocationsRequest, Seq[ReturnedLocation]]

  def validityFilter(req: LocationsRequest): Boolean

  def isUp: Boolean

  def close(): Unit
}
