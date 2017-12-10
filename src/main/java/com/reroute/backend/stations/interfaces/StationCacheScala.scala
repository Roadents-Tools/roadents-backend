package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

trait StationCacheScala {

  def servesRange(center: LocationPointScala, area: DistanceScala): Boolean

  def servesPoint(center: LocationPointScala): Boolean = servesRange(center, DistanceScala.ERROR_MARGIN)

  def getStartingStations(start: StartScala, dist: DistanceScala): Option[List[StationScala]]

  def getTransferStations(station: StationScala, range: DistanceScala): Option[List[StationScala]]

  def getTransferStationsBulk(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    request.view.map(req => (req, getTransferStations(req.station, req.distance))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): Option[List[StationWithRoute]]

  def getPathsForStationBulk(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    request.map(req => (req, getPathsForStation(req.station, req.starttime, req.maxdelta))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): Option[List[StationWithRoute]]

  def getArrivableStationBulk(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    request.map(req => (req, getArrivableStations(req.station, req.starttime, req.maxdelta))).collect({
      case (key, Some(res)) => key -> res
    }).toMap
  }

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: List[StationScala]): Boolean

  def putTransferStations(station: StationScala, range: DistanceScala, stations: List[StationScala]): Boolean

  def putTransferStationsBulk(request: Seq[(TransferRequest, List[StationScala])]): Boolean = {
    request.forall({ case (req, items) => putTransferStations(req.station, req.distance, items) })
  }

  def putPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala, results: List[StationWithRoute]): Boolean

  def putPathsForStationBulk(request: Seq[(PathsRequest, List[StationWithRoute])]): Boolean = {
    request.forall({ case (req, items) => putPathsForStation(req.station, req.starttime, req.maxdelta, items) })
  }

  def putArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala, staitons: List[StationWithRoute]): Boolean

  def putArrivableStationBulk(request: Seq[(ArrivableRequest, List[StationWithRoute])]): Boolean = {
    request.forall({ case (req, items) => putArrivableStations(req.station, req.starttime, req.maxdelta, items) })
  }

  def close(): Unit

  def isUp: Boolean
}
