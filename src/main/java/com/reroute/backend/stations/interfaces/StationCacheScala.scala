package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationCacheScala {

  def servesRange(center: LocationPointScala, area: DistanceScala): Boolean

  def servesPoint(center: LocationPointScala): Boolean = servesRange(center, DistanceScala.ERROR_MARGIN)

  def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int): Option[Seq[StationScala]]

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[StationScala]]

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]]

  def getArrivableStation(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]]

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: Seq[StationScala]): Boolean

  def putTransferStations(request: Seq[(TransferRequest, Seq[StationScala])]): Boolean

  def putPathsForStation(request: Seq[(PathsRequest, Seq[StationWithRoute])]): Boolean

  def putArrivableStation(request: Seq[(ArrivableRequest, Seq[StationWithRoute])]): Boolean

  def close(): Unit

  def isUp: Boolean
}
