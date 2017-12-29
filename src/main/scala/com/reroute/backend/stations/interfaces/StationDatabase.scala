package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, LocationPoint, Station, StationWithRoute}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationDatabase {

  def getStartingStations(start: InputLocation, dist: Distance, limit: Int = Int.MaxValue): Seq[Station]

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[Station]]

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]]

  def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]]

  def databaseName: String

  def servesPoint(point: LocationPoint): Boolean

  def servesArea(point: LocationPoint, range: Distance): Boolean

  def close(): Unit

  def isUp: Boolean
}
