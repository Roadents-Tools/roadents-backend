package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationDatabaseScala {

  def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int = Int.MaxValue): Seq[StationScala]

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[StationScala]]

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]]

  def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]]

  def databaseName: String

  def servesPoint(point: LocationPointScala): Boolean

  def servesArea(point: LocationPointScala, range: DistanceScala): Boolean

  def close(): Unit

  def isUp: Boolean
}
