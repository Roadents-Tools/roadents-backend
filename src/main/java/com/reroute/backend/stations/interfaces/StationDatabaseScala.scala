package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationDatabaseScala {

  def getStartingStations(start: StartScala, dist: DistanceScala): List[StationScala]

  def getTransferStations(station: StationScala, range: DistanceScala): List[StationScala]

  def getTransferStationsBulk(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    request.map(req => req -> getTransferStations(req.station, req.distance)).toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): List[StationWithRoute]

  def getPathsForStationBulk(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    request.map(req => (req, getPathsForStation(req.station, req.starttime, req.maxdelta))).toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute]

  def getArrivableStationBulk(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    request.map(req => (req, getArrivableStations(req.station, req.starttime, req.maxdelta))).toMap
  }

  def databaseName: String

  def servesPoint(point: LocationPointScala): Boolean

  def servesArea(point: LocationPointScala, range: DistanceScala): Boolean

  def close(): Unit

  def isUp: Boolean

  def loadData(data: Seq[StationWithRoute]): Boolean
}
