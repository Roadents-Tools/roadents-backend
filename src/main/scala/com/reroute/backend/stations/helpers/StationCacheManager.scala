package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, Station, StationWithRoute}
import com.reroute.backend.stations.interfaces.StationCache
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationCacheManager {

  private var cachesInUse: Seq[StationCache] = Nil

  def setTest(test: Boolean): Unit = {
    cachesInUse = initializeCaches(test)
  }

  def getStartingStations(start: InputLocation, dist: Distance, limit: Int = Int.MaxValue): Option[Seq[Station]] = {
    caches.view
      .filter(_.isUp)
      .filter(_.servesRange(start, dist))
      .map(_.getStartingStations(start, dist, limit))
      .find(_.isDefined)
      .flatten
  }

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[Station]] = {
    val rval = mutable.Map[TransferRequest, Seq[Station]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getTransferStations(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[PathsRequest, Seq[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getPathsForStation(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  private def caches: Seq[StationCache] = {
    if (cachesInUse == Nil) {
      cachesInUse = initializeCaches()
    }
    cachesInUse
  }

  private def initializeCaches(test: Boolean = false): Seq[StationCache] = {
    if (test) Seq.empty else Seq()
  }

  def getArrivableStation(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, Seq[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getArrivableStation(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  def putStartingStations(start: InputLocation, dist: Distance, stations: Seq[Station]): Boolean = {
    anyWorks(cache => cache.servesRange(start, dist) && cache.putStartingStations(start, dist, stations))
  }

  private def anyWorks(pred: StationCache => Boolean): Boolean = caches.filter(_.isUp).exists(pred(_))

  def putTransferStations(request: Seq[(TransferRequest, Seq[Station])]): Boolean = {
    anyWorks(_.putTransferStations(request))
  }

  def close(): Unit = caches.foreach(_.close())

  def putPathsForStation(request: Seq[(PathsRequest, Seq[StationWithRoute])]): Boolean = {
    anyWorks(_.putPathsForStation(request))
  }

  def putArrivableStation(request: Seq[(ArrivableRequest, Seq[StationWithRoute])]): Boolean = {
    anyWorks(_.putArrivableStation(request))
  }
}
