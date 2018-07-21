package com.reroute.backend.logic.revstationroute

import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDelta
import com.reroute.backend.stations.TransitDataRetriever
import com.typesafe.scalalogging.Logger

import scala.collection.mutable
import scala.util.{Success, Try}

object RevStationRouteGenerator extends LogicCore[RevStationRouteRequest] {

  private final val logger = Logger[RevStationRouteGenerator.type]

  def buildStationRouteList(req: RevStationRouteRequest): Seq[Route] = {

    // We don't want multiple routes to the same location, so we store a map from a (hashed) location
    // to the quickest route to that location.
    val rval = mutable.LinkedHashMap[Int, Route]()

    // Construct the first layer from the start information
    val baseRoute = new Route(req.start, req.starttime)
    if (req.yieldFilter(baseRoute)) rval.put(locationTag(req.start), baseRoute)
    var layer = nextLayer(Seq(baseRoute), req).filter(req.branchFilter).take(req.branchLimit)
    layer
      .filter(!_.currentEnd.overlaps(req.start)) //Don't go back to a previous location
      .filter(req.yieldFilter)
      .foreach(elem => rval.put(locationTag(elem.currentEnd), elem))

    // Keep constructing new layers
    while (layer.nonEmpty) {
      logger.info(s"LAYER OF SIZE ${layer.size}")
      val rawlayer = nextLayer(layer, req)
      logger.info(s"RAWLAYER OF SIZE ${rawlayer.size}")
      rawlayer
        .filter(req.yieldFilter)
        .filter(rt => {
          // We only keep and progress routes that either go to new locations, or if they go to old
          // locations are faster than what we had before.
          rval.get(locationTag(rt.currentEnd)).forall(existing => existing.totalTime > rt.totalTime)
        })
        .foreach(rt => rval.put(locationTag(rt.currentEnd), rt))
      layer = rawlayer.filter(req.branchFilter).take(req.branchLimit)
      logger.info(s"FILTEREDLAYER OF SIZE ${layer.size}")
    }

    // We may sort by something else in the future, or have it user set, so we do it at the end.
    rval.values.toStream.sortBy(_.steps.size).take(req.yieldLimit)
  }

  @inline
  private def locationTag(loc: LocationPoint): Int = {
    (loc.latitude.toString + ":" + loc.longitude.toString).hashCode
  }

  override val tag: String = "REV_STATION_ROUTE"

  override def runLogic(request: RevStationRouteRequest): ApplicationResult = Try {
    ApplicationResult.Result(buildStationRouteList(request))
  } recoverWith {
    case e: Throwable => Success(ApplicationResult.Error(Seq(e.getMessage)))
  } getOrElse ApplicationResult.Error(Seq("An unknown error occurred."))

  @inline
  private def nextLayer(curlayer: Seq[Route], req: RevStationRouteRequest) = {

    // A lot of the per-request limits are calced from the size of the current layer so we dont waste too much
    // time on results we throw away anyway
    val layerSize = curlayer.size

    // We build a map of walkrequest -> route so that we can quickly pair up the results later
    val walkQueryMap = curlayer.map(rt => req.queryGenerator.genWalkableQuery(rt, layerSize) -> rt).toMap
    val rawWalkResults = TransitDataRetriever.getWalkableStations(walkQueryMap.keys.toSeq)
      .map({ case (curreq, curres) => (walkQueryMap(curreq), curres) })

    val walkFilter: (Route, StationWithRoute) => Boolean = buildWalkFilter(req)
    val walkResults = rawWalkResults.map({ case (key, value) =>
      (key, value.filter(walkFilter(key, _)))
    })

    val transitLayerSize = walkResults.map(_._2.length).sum

    logger.info(s"WALKABLE OF SIZE $transitLayerSize")

    val max_walkable_per = (req.branchLimit * 4.0 / layerSize + 5).toInt

    // We build of nested map of route -> (walkable -> (arrivable request)) because we want to quickly
    // build the routes at the end but at the same time routes don't keep the full StationWithRoute object
    // when we add them to a step to add them to a route
    val arrivableQueryMap = walkResults.map({ case (key, value) =>
      val nvalue = value
        .take(max_walkable_per)
        .map(walkinfo => walkinfo -> req.queryGenerator.genDepartableQuery(key, walkinfo, transitLayerSize))
        .toMap
      (key, nvalue)
    })

    val rawArriveResults = TransitDataRetriever.getArrivableStations(arrivableQueryMap.values.flatMap(_.values).toSeq)

    // Iterate over each of the (route, walkable) pairs and them map its arrivable result to the fully constructed
    // route.
    val rval = for (
      (baseroute, aqmap) <- arrivableQueryMap;
      (walkable, arrivableRequest) <- aqmap
    ) yield {
      val walktime = -1 * baseroute.currentEnd.distanceTo(walkable.station).avgWalkTime.abs
      val walkroute = baseroute + GeneralWalkStep(baseroute.currentEnd, walkable.station, walktime)

      rawArriveResults(arrivableRequest)
        .map(arrivedStation => {
          val delayedStart = walkable.prevArrival(walkroute.endTime)
          val waitTime = -1 * delayedStart.timeUntil(walkroute.endTime).abs
          val travelNode = RevDataTransitStep(walkable, arrivedStation, delayedStart - TimeDelta.SECOND, waitTime)
          walkroute + travelNode
        })
    }

    rval.flatten.toSeq
  }

  @inline
  private def buildWalkFilter(req: RevStationRouteRequest)(baseroute: Route, nextinfo: StationWithRoute): Boolean = {
    if (baseroute.hasPoint(nextinfo.station) && !baseroute.currentEnd.overlaps(nextinfo.station)) {
      return false
    }
    val walktime = -1 * baseroute.currentEnd.distanceTo(nextinfo.station).avgWalkTime.abs
    val nextroute = baseroute + GeneralWalkStep(baseroute.currentEnd, nextinfo.station, walktime)
    req.branchFilter(nextroute)
  }
}
