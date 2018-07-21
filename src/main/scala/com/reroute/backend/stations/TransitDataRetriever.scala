package com.reroute.backend.stations

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.stations.helpers.{StationCacheManager, StationDatabaseManager}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.typesafe.scalalogging.Logger

object TransitDataRetriever extends StationDatabase {
  private final val logger = Logger[TransitDataRetriever.type]

  override def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]] = {
    val cached = StationCacheManager.getWalkableStations(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getWalkableStations(remaining)
    StationCacheManager.cacheWalkableRequests(dbres)
    cached ++ dbres
  }

  override def databaseName: String = "GlobalTransitDatabase"

  override def close(): Unit = {
    StationCacheManager.close()
    StationDatabaseManager.close()
  }

  override def isUp: Boolean = {
    StationDatabaseManager.isUp
  }

  def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val cached = StationCacheManager.getArrivableStations(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getArrivableStations(remaining)
    StationCacheManager.cacheArrivableRequests(dbres)
    cached ++ dbres
  }
}
