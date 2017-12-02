package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationCacheScala

import scala.collection.mutable

object StationCacheManager {

  private var cachesInUse: List[StationCacheScala] = Nil

  def setTest(test: Boolean): Unit = {
    cachesInUse = initializeCaches(test)
  }

  def getStartingStations(start: StartScala, dist: DistanceScala): Option[List[StationScala]] = {
    getFromCaches(_.getStartingStations(start, dist))
  }

  def getTransferStations(station: StationScala, range: DistanceScala): Option[List[StationScala]] = {
    getFromCaches(_.getTransferStations(station, range))
  }

  def getTransferStationsBulk(request: List[(StationScala, DistanceScala)]): Map[StationScala, List[StationScala]] = {
    val rval = mutable.Map[StationScala, List[StationScala]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req._1).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getTransferStationsBulk(request.filter(req => rval.get(req._1).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): Option[List[StationWithRoute]] = {
    getFromCaches(_.getPathsForStation(station, starttime, maxdelta))
  }

  def getPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala)]): Map[StationScala, List[StationWithRoute]] = {
    val rval = mutable.Map[StationScala, List[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req._1).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getPathsForStationBulk(request.filter(req => rval.get(req._1).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): Option[List[StationWithRoute]] = {
    getFromCaches(_.getArrivableStations(start, starttime, maxDelta))
  }

  private def getFromCaches[T](func: StationCacheScala => Option[T]): Option[T] = caches.view
    .filter(_.isUp)
    .map(func(_))
    .find(_.isDefined)
    .flatten

  def getArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala)]): Map[StationWithRoute, List[StationWithRoute]] = {
    val rval = mutable.Map[StationWithRoute, List[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req._1).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getArrivableStationBulk(request.filter(req => rval.get(req._1).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: List[StationScala]): Boolean = {
    anyWorks(_.putStartingStations(start, dist, stations))
  }

  def putTransferStations(station: StationScala, range: DistanceScala, stations: List[StationScala]): Boolean = {
    anyWorks(_.putTransferStations(station, range, stations))
  }

  def putTransferStationsBulk(request: List[(StationScala, DistanceScala, List[StationScala])]): Boolean = {
    anyWorks(_.putTransferStationsBulk(request))
  }

  def putPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala, results: List[StationWithRoute]): Boolean = {
    anyWorks(_.putPathsForStation(station, starttime, maxdelta, results))
  }

  def putPathsForStationBulk(request: List[(StationScala, TimePointScala, TimeDeltaScala, List[StationWithRoute])]): Boolean = {
    anyWorks(_.putPathsForStationBulk(request))
  }

  def putArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala, staitons: List[StationWithRoute]): Boolean = {
    anyWorks(_.putArrivableStations(start, starttime, maxDelta, staitons))
  }

  private def anyWorks(pred: StationCacheScala => Boolean): Boolean = caches.filter(_.isUp).exists(pred(_))

  def putArrivableStationBulk(request: List[(StationWithRoute, TimePointScala, TimeDeltaScala, List[StationWithRoute])]): Boolean = {
    anyWorks(_.putArrivableStationBulk(request))
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
