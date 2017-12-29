package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, Station, StationWithRoute}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.postgresql.{PostgresGtfsDb, PostgresGtfsDbConfig}
import com.reroute.backend.stations.test.TestStationDb
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationDatabaseManager {

  private var databasesLoaded: Seq[StationDatabase] = Nil

  def setTest(test: Boolean): Unit = {
    databasesLoaded = initializeDatabases(test)
  }

  def getStartingStations(start: InputLocation, dist: Distance, limit: Int = Int.MaxValue): Seq[Station] = {
    databases.view
      .filter(_.isUp)
      .filter(_.servesPoint(start))
      .map(_.getStartingStations(start, dist, limit))
      .find(_.nonEmpty)
      .getOrElse(Seq.empty)
  }

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[Station]] = {
    var rval = mutable.Map[TransferRequest, Seq[Station]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesArea(req.station, req.distance))
      rval ++= cache.getTransferStations(serviceable)
    })
    request.view
      .filter(req => rval.get(req).isEmpty)
      .foreach(req => rval += (req -> Seq.empty))
    rval.toMap
  }

  private def databases: Seq[StationDatabase] = {
    if (databasesLoaded == Nil) {
      databasesLoaded = initializeDatabases()
    }
    databasesLoaded
  }

  private def initializeDatabases(test: Boolean = false): Seq[StationDatabase] = {
    if (!test) Seq(new PostgresGtfsDb(PostgresGtfsDbConfig(
      dbname = "localdb",
      dburl = "jdbc:postgresql://localhost:5432/Test_GTFS2"
    ))) else Seq(new TestStationDb())
  }

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[PathsRequest, Seq[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesPoint(req.station))
      rval ++= cache.getPathsForStation(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> Seq.empty))
    rval.toMap
  }

  def getArrivableStation(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, Seq[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining.filter(req => cache.servesPoint(req.station.station))
      rval ++= cache.getArrivableStations(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> Seq.empty))
    rval.toMap
  }

  def close(): Unit = databases.foreach(_.close())

}
