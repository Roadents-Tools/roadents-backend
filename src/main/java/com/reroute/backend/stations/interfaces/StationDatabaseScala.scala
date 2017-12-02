package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

trait StationDatabaseScala {

  def getStartingStations(start: StartScala, dist: DistanceScala): List[StationScala]

  def getTransferStations(station: StationScala, range: DistanceScala): List[StationScala]

  def getTransferStationsBulk(request: List[(StationScala, DistanceScala)]): Map[StationScala, List[StationScala]] = {
    request.map(req => (req._1, getTransferStations(req._1, req._2))).toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): List[StationWithRoute]

  def getPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala)]): Map[StationScala, List[StationWithRoute]] = {
    request.map(req => (req._1, getPathsForStation(req._1, req._2, req._3))).toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute]

  def getArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala)]): Map[StationWithRoute, List[StationWithRoute]] = {
    request.map(req => (req._1, getArrivableStations(req._1, req._2, req._3))).toMap
  }

  def databaseName: String

  def servesPoint(point: LocationPointScala): Boolean

  def servesArea(point: LocationPointScala, range: DistanceScala): Boolean

  def close(): Unit

  def isUp: Boolean

  def loadData(data: Seq[StationWithRoute]): Boolean
}
