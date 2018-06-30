package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, LocationPoint, Station, StationWithRoute}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationCache {

  def servesRange(center: LocationPoint, area: Distance): Boolean

  def servesPoint(center: LocationPoint): Boolean = servesRange(center, Distance.ERROR_MARGIN)

  def getStartingStations(start: InputLocation, dist: Distance, limit: Int): Option[Seq[Station]]

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[Station]]

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]]

  def getArrivableStation(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]]

  def putStartingStations(start: InputLocation, dist: Distance, stations: Seq[Station]): Boolean

  def putTransferStations(request: Seq[(TransferRequest, Seq[Station])]): Boolean

  def putPathsForStation(request: Seq[(PathsRequest, Seq[StationWithRoute])]): Boolean

  def putArrivableStation(request: Seq[(ArrivableRequest, Seq[StationWithRoute])]): Boolean

  def close(): Unit

  def isUp: Boolean
}
