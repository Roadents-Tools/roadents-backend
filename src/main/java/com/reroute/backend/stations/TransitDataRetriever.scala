package com.reroute.backend.stations

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.helpers.{StationCacheManager, StationDatabaseManager}

object TransitDataRetriever {

  def getStartingStations(start: StartScala, dist: DistanceScala): List[StationScala] = {
    val cached = StationCacheManager.getStartingStations(start, dist)
    cached match {
      case Some(res) => res
      case None => val dbres = StationDatabaseManager.getStartingStations(start, dist)
        StationCacheManager.putStartingStations(start, dist, dbres)
        dbres
    }
  }

  def getTransferStations(station: StationScala, range: DistanceScala): List[StationScala] = {
    val cached = StationCacheManager.getTransferStations(station, range)
    cached match {
      case Some(res) => res
      case None => val dbres = StationDatabaseManager.getTransferStations(station, range)
        StationCacheManager.putTransferStations(station, range, dbres)
        dbres
    }
  }

  def getTransferStationsBulk(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    val cached = StationCacheManager.getTransferStationsBulk(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getTransferStationsBulk(remaining)
    StationCacheManager.putTransferStationsBulk(dbres.toSeq)
    cached ++ dbres
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): List[StationWithRoute] = {
    val cached = StationCacheManager.getPathsForStation(station, starttime, maxdelta)
    cached match {
      case Some(res) => res
      case None => val dbres = StationDatabaseManager.getPathsForStation(station, starttime, maxdelta)
        StationCacheManager.putPathsForStation(station, starttime, maxdelta, dbres)
        dbres
    }
  }

  def getPathsForStationBulk(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    val cached = StationCacheManager.getPathsForStationBulk(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getPathsForStationBulk(remaining)
    StationCacheManager.putPathsForStationBulk(dbres.toSeq)
    cached ++ dbres
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute] = {
    val cached = StationCacheManager.getArrivableStations(start, starttime, maxDelta)
    cached match {
      case Some(res) => res
      case None => val dbres = StationDatabaseManager.getArrivableStations(start, starttime, maxDelta)
        StationCacheManager.putArrivableStations(start, starttime, maxDelta, dbres)
        dbres
    }
  }

  def getArrivableStationBulk(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    val cached = StationCacheManager.getArrivableStationBulk(request)
    val remaining = request.filter(req => cached.get(req).isEmpty)
    if (remaining.isEmpty) return cached
    val dbres = StationDatabaseManager.getArrivableStationBulk(remaining)
    StationCacheManager.putArrivableStationBulk(dbres.toSeq)
    cached ++ dbres
  }
}
