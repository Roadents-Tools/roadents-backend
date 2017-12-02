package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

trait StationCacheScala {

  def getStartingStations(start: StartScala, dist: DistanceScala): Option[List[StationScala]]

  def getTransferStations(station: StationScala, range: DistanceScala): Option[List[StationScala]]

  def getTransferStationsBulk(request: List[(StationScala, DistanceScala)]): Map[StationScala, List[StationScala]] = {
    request.view.map(req => (req._1, getTransferStations(req._1, req._2))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): Option[List[StationWithRoute]]

  def getPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala)]): Map[StationScala, List[StationWithRoute]] = {
    request.map(req => (req._1, getPathsForStation(req._1, req._2, req._3))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): Option[List[StationWithRoute]]

  def getArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala)]): Map[StationWithRoute, List[StationWithRoute]] = {
    request.map(req => (req._1, getArrivableStations(req._1, req._2, req._3))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: List[StationScala]): Boolean

  def putTransferStations(station: StationScala, range: DistanceScala, stations: List[StationScala]): Boolean

  def putTransferStationsBulk(request: List[(StationScala, DistanceScala, List[StationScala])]): Boolean = {
    request.forall(req => putTransferStations(req._1, req._2, req._3))
  }

  def putPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala, results: List[StationWithRoute]): Boolean

  def putPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala, List[StationWithRoute])]): Boolean = {
    request.forall(req => putPathsForStation(req._1, req._2, req._3, req._4))
  }

  def putArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala, staitons: List[StationWithRoute]): Boolean

  def putArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala, List[StationWithRoute])]): Boolean = {
    request.forall(req => putArrivableStations(req._1, req._2, req._3, req._4))
  }

  def close(): Unit

  def isUp: Boolean
}
