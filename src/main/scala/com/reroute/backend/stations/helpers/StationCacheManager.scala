package com.reroute.backend.stations.helpers

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StartScala, StationScala, StationWithRoute}
import com.reroute.backend.stations.interfaces.StationCacheScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.mutable

object StationCacheManager {

  private var cachesInUse: Seq[StationCacheScala] = Nil

  def setTest(test: Boolean): Unit = {
    cachesInUse = initializeCaches(test)
  }

  def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int = Int.MaxValue): Option[Seq[StationScala]] = {
    caches.view
      .filter(_.isUp)
      .filter(_.servesRange(start, dist))
      .map(_.getStartingStations(start, dist, limit))
      .find(_.isDefined)
      .flatten
  }

  def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[StationScala]] = {
    val rval = mutable.Map[TransferRequest, Seq[StationScala]]()
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

  private def caches: Seq[StationCacheScala] = {
    if (cachesInUse == Nil) {
      cachesInUse = initializeCaches()
    }
    cachesInUse
  }

  private def initializeCaches(test: Boolean = false): Seq[StationCacheScala] = {
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

  def putStartingStations(start: StartScala, dist: DistanceScala, stations: Seq[StationScala]): Boolean = {
    anyWorks(cache => cache.servesRange(start, dist) && cache.putStartingStations(start, dist, stations))
  }

  private def anyWorks(pred: StationCacheScala => Boolean): Boolean = caches.filter(_.isUp).exists(pred(_))

  def putTransferStations(request: Seq[(TransferRequest, Seq[StationScala])]): Boolean = {
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
