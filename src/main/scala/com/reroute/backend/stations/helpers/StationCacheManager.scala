package com.reroute.backend.stations.helpers

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.stations.interfaces.StationCache
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}

import scala.collection.mutable

object StationCacheManager extends StationCache {


  override def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[WalkableRequest, Seq[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getWalkableStations(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  override def databaseName: String = "StationCacheRouter"

  override def isUp: Boolean = caches.exists(_.isUp)

  private var cachesInUse: Seq[StationCache] = Nil

  def setTest(test: Boolean): Unit = {
    cachesInUse = initializeCaches(test)
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

  def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val rval = mutable.Map[ArrivableRequest, Seq[StationWithRoute]]()
    caches.view
      .takeWhile(_ => request.exists(req => rval.get(req).isEmpty))
      .filter(_.isUp)
      .map(cache => cache.getArrivableStations(request.filter(req => rval.get(req).isEmpty)))
      .foreach(rval ++= _)
    rval.toMap
  }

  private def anyWorks(pred: StationCache => Boolean): Boolean = caches.filter(_.isUp).exists(pred(_))

  override def cacheArrivableRequests(data: Map[ArrivableRequest, Seq[StationWithRoute]]): Boolean = anyWorks(_.cacheArrivableRequests(data))

  override def cacheWalkableRequests(data: Map[WalkableRequest, Seq[StationWithRoute]]): Boolean = anyWorks(_.cacheWalkableRequests(data))

  def close(): Unit = caches.foreach(_.close())

}
