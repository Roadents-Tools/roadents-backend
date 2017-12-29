package com.reroute.backend.locations.interfaces

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.model.location.DestinationScala

trait LocationProviderScala {

  def queryLocations(requests: Seq[LocationsRequest]): Map[LocationsRequest, Seq[DestinationScala]]

  def validityFilter(req: LocationsRequest): Boolean

  def isUp(): Boolean

  def close(): Unit
}
