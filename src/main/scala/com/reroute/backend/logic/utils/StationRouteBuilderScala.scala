package com.reroute.backend.logic.utils

import com.reroute.backend.model.distance.DistanceUnitsScala
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDeltaScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest, TransitDataRetriever}

import scala.collection.mutable

object StationRouteBuilderScala {

  private final val LimitScale = 1.0

  def buildStationRouteList(req: StationRouteBuildRequestScala): Seq[RouteScala] = {
    val rval = mutable.LinkedHashMap[Int, RouteScala]()
    rval.put(locationTag(req.start), new RouteScala(req.start, req.starttime))

    val seedRoutes = genStartRoutes(req).filter(req.routeValid).take(req.walkLimit)
    val seedTransit = genTransitRoutes(seedRoutes, req).filter(req.routeValid).take(req.transitlimit)
    seedTransit.filter(req.meetsMinimums).foreach(elem => rval.put(locationTag(elem.currentEnd), elem))
    var layer = seedTransit
    while (layer.nonEmpty) {
      println(s"LAYER OF SIZE ${layer.size}")
      val rawlayer = nextLayer(layer, req)
      println(s"RAWLAYER OF SIZE ${rawlayer.size}")
      layer = rawlayer.filter(rt => {
        rval.get(locationTag(rt.currentEnd)).forall(existing => existing.totalTime > rt.totalTime)
      })
      layer.filter(req.meetsMinimums).foreach(rt => rval.put(locationTag(rt.currentEnd), rt))
      println(s"FILTEREDLAYER OF SIZE ${layer.size}")
    }
    rval.values.toStream.sortBy(_.steps.size).take(req.finallimit)
  }

  @inline
  private def genStartRoutes(req: StationRouteBuildRequestScala): Seq[RouteScala] = {
    val baseroute = new RouteScala(req.start, req.starttime)
    getStartNodes(req).map(step => baseroute + step)
  }

  @inline
  private def getStartNodes(req: StationRouteBuildRequestScala): Seq[StartWalkStep] = {
    TransitDataRetriever.getStartingStations(req.start, req.delta.total_max.avgWalkDist, req.walkLimit)
      .map(stat => {
        val walkMinutes = req.start.distanceTo(stat).in(DistanceUnitsScala.AVG_WALK_MINUTES)
        val walkTime = TimeDeltaScala((walkMinutes * 60000).asInstanceOf[Long])
        StartWalkStep(req.start, stat, walkTime)
      })
  }

  @inline
  private def nextLayer(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = {
    val walklayer = genTransferRoutes(curlayer, req).toStream
      .filter(req.routeValid)
      .take(req.walkLimit)
    genTransitRoutes(walklayer, req).toStream
      .filter(req.routeValid)
      .take(req.transitlimit)
  }

  @inline
  private def genTransferRoutes(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = genTransferNodes(curlayer, req)
    .flatMap({ case (route, possiblesteps) => possiblesteps.map(step => route + step) })

  @inline
  private def genTransferNodes(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = {
    val reqsMap = curlayer.map(rt => rt -> buildTransferReq(rt, req)).toMap

    val res = TransitDataRetriever.getTransferStations(reqsMap.values.toSeq)
    val rval = curlayer
      .filter(rt => rt.currentEnd.isInstanceOf[StationScala])
      .map(rt => rt -> res(reqsMap(rt)).toStream
        .filter(st => rt.currentEnd.distanceTo(st) < (req.delta.total_max - rt.totalTime).avgWalkDist && !rt.hasPoint(st))
        .map(st => TransferWalkStep(rt.currentEnd.asInstanceOf[StationScala], st, rt.currentEnd.distanceTo(st).avgWalkTime))
      )
    rval
  }

  @inline
  private def buildTransferReq(rt: RouteScala, req: StationRouteBuildRequestScala): TransferRequest = {
    val usableDist = Seq(
      req.transferTime.total_max - rt.transferTime,
      req.transferTime.max,
      req.delta.total_max - rt.totalTime
    ).min.avgWalkDist
    TransferRequest(rt.currentEnd.asInstanceOf[StationScala], usableDist, req.walkLimit)
  }

  @inline
  private def locationTag(loc: LocationPointScala): Int = {
    (loc.latitude.toString + ":" + loc.longitude.toString).hashCode
  }

  @inline
  private def genTransitRoutes(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala): Seq[RouteScala] = {
    if (curlayer.isEmpty) return Seq.empty
    val layersize = curlayer.size
    val pathsLimit = if (req.pathslimit == Int.MaxValue) Int.MaxValue else (req.pathslimit * LimitScale / layersize).toInt
    val pathReqs = curlayer
      .withFilter(rt => rt.currentEnd.isInstanceOf[StationScala])
      .map(rt => {
        val usableTime = Seq(
          req.waitTime.max,
          req.waitTime.total_max - rt.waitTime,
          req.delta.total_max - rt.totalTime
        ).min
        rt -> PathsRequest(rt.currentEnd.asInstanceOf[StationScala], rt.endTime, usableTime, pathsLimit)
      })
      .toMap

    val pathResults = TransitDataRetriever.getPathsForStation(pathReqs.values.toSeq)

    val arrivablereqs = pathReqs.flatMap({
      case (route, chainreq) =>
        pathResults(chainreq).map(data => data -> buildArrivalReq(route, data, req, layersize))
    })

    val arrivableres = TransitDataRetriever.getArrivableStation(arrivablereqs.values.toSeq)

    val rval = for (rt <- curlayer; startStation <- pathResults(pathReqs(rt))) yield {
      val tstart = startStation.nextDeparture(rt.endTime)
      val waittime = rt.endTime timeUntil tstart
      if (waittime > TimeDeltaScala.NULL) arrivableres(arrivablereqs(startStation))
        .filter(st => !rt.hasPoint(st.station))
        .map(FromDataTransitStep(startStation, _, tstart, waittime))
        .map(rt + _)
      else Seq.empty
    }
    rval.flatten
  }

  @inline
  private def buildArrivalReq(route: RouteScala, chaininfo: StationWithRoute, req: StationRouteBuildRequestScala, curlayer: Int): ArrivableRequest = {
    val trueStart = chaininfo.nextDeparture(route.endTime)
    val waitTime = route.endTime.timeUntil(trueStart)
    val truedelta = Seq(
      req.delta.total_max - route.totalTime - waitTime,
      req.waitTime.total_max - route.waitTime - waitTime,
      req.waitTime.max
    ).min
    val limit = if (req.transitlimit == Int.MaxValue) Int.MaxValue else (req.transitlimit * LimitScale / curlayer).toInt
    ArrivableRequest(chaininfo, trueStart, truedelta, limit)
  }
}
