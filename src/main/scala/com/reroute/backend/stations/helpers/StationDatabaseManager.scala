package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.stations.interfaces.StationDatabaseScala
import com.reroute.backend.stations.test.TestStationDbScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationDatabaseManager {

  private var databasesLoaded: Seq[StationDatabaseScala] = Nil

  def setTest(test: Boolean): Unit = {
    databasesLoaded = initializeDatabases(test)
  }

  def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int = Int.MaxValue): Seq[StationScala] = {
    databases.view
      .filter(_.isUp)
      .filter(_.servesPoint(start))
      .map(_.getStartingStations(start, dist, limit))
      .find(_.nonEmpty)
      .getOrElse(Seq.empty)
  }

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[StationScala]] = {
    var rval = mutable.Map[TransferRequest, Seq[StationScala]]()
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

  private def databases: Seq[StationDatabaseScala] = {
    if (databasesLoaded == Nil) {
      databasesLoaded = initializeDatabases()
    }
    databasesLoaded
  }

  private def initializeDatabases(test: Boolean = false): Seq[StationDatabaseScala] = {
    if (test) Seq() else Seq(new TestStationDbScala())
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
