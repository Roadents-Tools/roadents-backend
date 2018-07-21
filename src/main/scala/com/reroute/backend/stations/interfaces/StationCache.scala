package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}

trait StationCache extends StationDatabase {
  def cacheArrivableRequests(data: Map[ArrivableRequest, Seq[StationWithRoute]]): Boolean

  def cacheWalkableRequests(data: Map[WalkableRequest, Seq[StationWithRoute]]): Boolean
}
