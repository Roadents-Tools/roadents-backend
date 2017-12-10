package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationDatabaseScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationDatabaseManager {

  private var databasesLoaded: List[StationDatabaseScala] = Nil

  def setTest(test: Boolean): Unit = {
    databasesLoaded = initializeDatabases(test)
  }

  def getStartingStations(start: StartScala, dist: DistanceScala): List[StationScala] = {
    databases.view
      .filter(_.isUp)
      .filter(_.servesPoint(start))
      .map(_.getStartingStations(start, dist))
      .find(_.nonEmpty)
      .getOrElse(List.empty)
  }

  def getTransferStations(station: StationScala, range: DistanceScala): List[StationScala] = {
    databases.view
      .filter(_.isUp)
      .filter(_.servesArea(station, range))
      .map(_.getTransferStations(station, range))
      .find(_.nonEmpty)
      .getOrElse(List.empty)
  }

  def getTransferStationsBulk(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    var rval = mutable.Map[TransferRequest, List[StationScala]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesArea(req.station, req.distance))
      rval ++= cache.getTransferStationsBulk(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> List.empty))
    rval.toMap
  }

  private def databases: List[StationDatabaseScala] = {
    if (databasesLoaded == Nil) {
      databasesLoaded = initializeDatabases()
    }
    databasesLoaded
  }

  private def initializeDatabases(test: Boolean = false): List[StationDatabaseScala] = {
    if (test) List() else List()
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): List[StationWithRoute] = {
    databases.view
      .filter(_.isUp)
      .filter(db => db.servesPoint(station))
      .flatMap(_.getPathsForStation(station, starttime, maxdelta))
      .toList
  }

  def getPathsForStationBulk(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    val rval = mutable.Map[PathsRequest, List[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesPoint(req.station))
      rval ++= cache.getPathsForStationBulk(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> List.empty))
    rval.toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute] = {
    databases.view
      .filter(_.isUp)
      .find(db => db.servesPoint(start.station))
      .map(_.getArrivableStations(start, starttime, maxDelta))
      .getOrElse(List.empty)
  }

  def getArrivableStationBulk(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, List[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesPoint(req.station.station))
      rval ++= cache.getArrivableStationBulk(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> List.empty))
    rval.toMap
  }

  def close(): Unit = databases.foreach(_.close())

}
