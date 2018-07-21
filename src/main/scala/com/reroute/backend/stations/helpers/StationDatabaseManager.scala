package com.reroute.backend.stations.helpers

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.postgresql.PostgresGtfsDb
import com.reroute.backend.stations.test.TestStationDb
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}
import com.reroute.backend.utils.postgres.PostgresConfig

import scala.collection.mutable

object StationDatabaseManager extends StationDatabase {

  private var databasesLoaded: Seq[StationDatabase] = Nil

  def setTest(test: Boolean): Unit = {
    databasesLoaded = initializeDatabases(test)
  }

  private def databases: Seq[StationDatabase] = {
    if (databasesLoaded == Nil) {
      databasesLoaded = initializeDatabases()
    }
    databasesLoaded
  }

  private def initializeDatabases(test: Boolean = false): Seq[StationDatabase] = {
    if (!test) {
      val nm = sys.env.getOrElse("STATDB_NAME", "EMPTY")
      val url = sys.env.getOrElse("STATDB_URL", "")
      Seq(new PostgresGtfsDb(PostgresConfig(
        dbname = nm,
        dburl = url
      )))
    }
    else {
      Seq(new TestStationDb())
    }
  }

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, Seq[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(cache => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining
      rval ++= cache.getArrivableStations(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> Seq.empty))
    rval.toMap
  }

  def close(): Unit = databases.foreach(_.close())

  override def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[WalkableRequest, Seq[StationWithRoute]]()
    databases.view.filter(_.isUp).foreach(db => {
      val remaining = request.filter(req => rval.get(req).isEmpty)
      if (remaining.isEmpty) {
        return rval.toMap
      }
      val serviceable = remaining
      rval ++= db.getWalkableStations(serviceable)
    })
    request.view.filter(req => rval.get(req).isEmpty).foreach(req => rval += (req -> Seq.empty))
    rval.toMap
  }

  override def databaseName: String = "StationDBRouter"

  override def isUp: Boolean = databases.exists(_.isUp)
}
