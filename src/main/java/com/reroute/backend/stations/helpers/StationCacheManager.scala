package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationCacheScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationCacheManager {

  private var cachesInUse: List[StationCacheScala] = Nil

  def setTest(test: Boolean): Unit = {
    cachesInUse = initializeCaches(test)
  }

  def getStartingStations(start: StartScala, dist: DistanceScala): Option[List[StationScala]] = {
    getFromCaches(_.getStartingStations(start, dist))(start, dist)
  }

  def getTransferStations(station: StationScala, range: DistanceScala): Option[List[StationScala]] = {
    getFromCaches(_.getTransferStations(station, range))(station, range)
  }

  private def getFromCaches[T](func: StationCacheScala => Option[T])(center: LocationPointScala, range: DistanceScala): Option[T] = caches.view
    .filter(_.isUp)
    .filter(_.servesRange(center, range))
    .map(func(_))
    .find(_.isDefined)
    .flatten

  def getTransferStationsBulk(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    val rval = mutable.Map[TransferRequest, List[StationScala]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getTransferStationsBulk(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): Option[List[StationWithRoute]] = {
    getFromCaches(_.getPathsForStation(station, starttime, maxdelta))(station, DistanceScala.ERROR_MARGIN)
  }

  def getPathsForStationBulk(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    val rval = mutable.Map[PathsRequest, List[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getPathsForStationBulk(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): Option[List[StationWithRoute]] = {
    getFromCaches(_.getArrivableStations(start, starttime, maxDelta))(start.station, maxDelta.avgWalkDist)
  }

  def getArrivableStationBulk(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, List[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getArrivableStationBulk(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: List[StationScala]): Boolean = {
    anyWorks(cache => cache.servesRange(start, dist) && cache.putStartingStations(start, dist, stations))
  }

  def putTransferStations(station: StationScala, range: DistanceScala, stations: List[StationScala]): Boolean = {
    anyWorks(cache => cache.servesRange(station, range) && cache.putTransferStations(station, range, stations))
  }

  def putTransferStationsBulk(request: Seq[(TransferRequest, List[StationScala])]): Boolean = {
    anyWorks(_.putTransferStationsBulk(request))
  }

  def putPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala, results: List[StationWithRoute]): Boolean = {
    anyWorks(_.putPathsForStation(station, starttime, maxdelta, results))
  }

  def putPathsForStationBulk(request: Seq[(PathsRequest, List[StationWithRoute])]): Boolean = {
    anyWorks(_.putPathsForStationBulk(request))
  }

  def putArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala, staitons: List[StationWithRoute]): Boolean = {
    anyWorks(_.putArrivableStations(start, starttime, maxDelta, staitons))
  }

  private def anyWorks(pred: StationCacheScala => Boolean): Boolean = caches.filter(_.isUp).exists(pred(_))

  def putArrivableStationBulk(request: Seq[(ArrivableRequest, List[StationWithRoute])]): Boolean = {
    anyWorks({
      _.putArrivableStationBulk(request)
    })
  }

  def close(): Unit = caches.foreach(_.close())

  private def caches: List[StationCacheScala] = {
    if (cachesInUse == Nil) {
      cachesInUse = initializeCaches()
    }
    cachesInUse
  }

  private def initializeCaches(test: Boolean = false): List[StationCacheScala] = {
    if (test) List.empty else List()
  }
}
