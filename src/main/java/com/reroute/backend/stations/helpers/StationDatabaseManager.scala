package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationDatabaseScala

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

  def getTransferStationsBulk(request: List[(StationScala, DistanceScala)]): Map[StationScala, List[StationScala]] = {
    var rval = mutable.Map[StationScala, List[StationScala]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req._1).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesArea(req._1, req._2))
      rval ++= cache.getTransferStationsBulk(serviceable)
    })
    request.view.filter(req => rval.get(req._1).isEmpty).foreach(req => rval += (req._1 -> List.empty))
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
      .filter(db => station.id.map(dbid => dbid.database == db.databaseName).getOrElse(db.servesPoint(station)))
      .flatMap(_.getPathsForStation(station, starttime, maxdelta))
      .toList
  }

  def getPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala)]): Map[StationScala, List[StationWithRoute]] = {
    val rval = mutable.Map[StationScala, List[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req._1).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesPoint(req._1))
      rval ++= cache.getPathsForStationBulk(serviceable)
    })
    request.view.filter(req => rval.get(req._1).isEmpty).foreach(req => rval += (req._1 -> List.empty))
    rval.toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute] = {
    databases.view
      .filter(_.isUp)
      .find(db => start.route.id.map(dbid => dbid.database == db.databaseName).getOrElse(db.servesPoint(start.station)))
      .map(_.getArrivableStations(start, starttime, maxDelta))
      .getOrElse(List.empty)
  }

  def getArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala)]): Map[StationWithRoute, List[StationWithRoute]] = {
    val rval = mutable.Map[StationWithRoute, List[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req._1).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => req._1.route.id.map(dbid => dbid.database == cache.databaseName).getOrElse(cache.servesPoint(req._1.station)))
      rval ++= cache.getArrivableStationBulk(serviceable)
    })
    request.view.filter(req => rval.get(req._1).isEmpty).foreach(req => rval += (req._1 -> List.empty))
    rval.toMap
  }

  def close(): Unit = databases.foreach(_.close())

}
