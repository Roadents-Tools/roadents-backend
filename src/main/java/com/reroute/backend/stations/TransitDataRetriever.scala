package com.reroute.backend.stations

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.stations.helpers.{StationCacheManager, StationDatabaseManager}

object TransitDataRetriever {

  def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int = Int.MaxValue): Seq[StationScala] = {
    val cached = StationCacheManager.getStartingStations(start, dist, limit)
    cached match {
      case Some(res) => res
      case None => val dbres = StationDatabaseManager.getStartingStations(start, dist, limit)
        StationCacheManager.putStartingStations(start, dist, dbres)
        dbres
    }
  }

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[StationScala]] = {
    val cached = StationCacheManager.getTransferStations(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getTransferStations(remaining)
    StationCacheManager.putTransferStations(dbres.toSeq)
    cached ++ dbres
  }

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]] = {
    val cached = StationCacheManager.getPathsForStation(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getPathsForStation(remaining)
    StationCacheManager.putPathsForStation(dbres.toSeq)
    cached ++ dbres
  }

  def getArrivableStation(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val cached = StationCacheManager.getArrivableStation(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getArrivableStation(remaining)
    StationCacheManager.putArrivableStation(dbres.toSeq)
    cached ++ dbres
  }
}
