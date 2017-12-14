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

    val seedRoutes = genStartRoutes(req).filter(routeWorks(req)).take(req.walklayerlimit)
    val seedTransit = genTransitRoutes(seedRoutes, req).filter(routeWorks(req)).take(req.transitlimit)
    seedTransit.foreach(elem => rval.put(locationTag(elem.currentEnd), elem))
    var layer = seedTransit
    while (layer.nonEmpty && rval.size < req.finallimit) {
      println(s"LAYER OF SIZE ${layer.size}")
      val rawlayer = nextLayer(layer, req)
      println(s"RAWLAYER OF SIZE ${rawlayer.size}")
      val filteredlayer = rawlayer
        .filter(rt => {
          rval.get(locationTag(rt.currentEnd)).forall(existing => existing.totalTime > rt.totalTime)
        })
        .map(rt => {
          rval.put(locationTag(rt.currentEnd), rt)
          rt
        })
      println(s"FILTEREDLAYER OF SIZE ${filteredlayer.size}")
      layer = filteredlayer
    }
    rval.values.take(req.finallimit).toSeq
  }

  @inline
  private def genStartRoutes(req: StationRouteBuildRequestScala): Seq[RouteScala] = {
    val baseroute = new RouteScala(req.start, req.starttime)
    getStartNodes(req).map(step => baseroute + step)
  }

  @inline
  private def getStartNodes(req: StationRouteBuildRequestScala): Seq[StartWalkStep] = {
    TransitDataRetriever.getStartingStations(req.start, req.delta.avgWalkDist, req.walklayerlimit).map(stat => StartWalkStep(
      req.start, stat,
      TimeDeltaScala((req.start.distanceTo(stat).in(DistanceUnitsScala.AVG_WALK_MINUTES) * 60000).asInstanceOf[Long])
    ))
  }

  @inline
  private def nextLayer(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = {
    val walklayerRaw = genTransferRoutes(curlayer, req)
    val walklayer = walklayerRaw.toStream.filter(routeWorks(req)).take(req.walklayerlimit)
    val transitlayer = genTransitRoutes(walklayer, req).toStream.filter(routeWorks(req)).take(req.transitlimit)
    transitlayer
  }

  @inline
  private def routeWorks(request: StationRouteBuildRequestScala): RouteScala => Boolean = route => {
    if (route.distance / route.totalTime.hours < LocationPointScala.AVG_WALKING_PER_HOUR) false

    else if (route.steps.lengthCompare(request.stepcount) > 0) false

    else {
      var walktotal = TimeDeltaScala.NULL
      var transfertotal = TimeDeltaScala.NULL
      var transittotal = TimeDeltaScala.NULL
      var total = TimeDeltaScala.NULL
      var rval = true
      route.steps.takeWhile(_ => rval).foreach({
        case st: TransferWalkStep =>
          if (st.totaltime > request.maxtransfertime) {
            rval = false
          }
          walktotal += st.totaltime
          transfertotal += st.totaltime
          total += st.totaltime
        case st: WalkStepScala =>
          if (st.totaltime > request.maxwalktime) {
            rval = false
          }
          walktotal += st.totaltime
          total += st.totaltime
        case st: TransitStepScala =>
          if (st.totaltime > request.maxtransittime) {
            rval = false
          }
          transittotal += st.totaltime
          total += st.totaltime
      })
      if (!rval) false
      else if (walktotal > request.totalwalktime) {
        false
      }
      else if (total > request.delta) {
        false
      }
      else if (transfertotal > request.totaltransfertime) {
        false
      }
      else if (transittotal > request.totaltransittime) {
        false
      }
      else true
    }
  }

  @inline
  private def genTransferRoutes(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = genTransferNodes(curlayer, req)
    .flatMap({ case (route, steps) => steps.map(step => route + step) })

  @inline
  private def genTransferNodes(curlayer: Seq[RouteScala], req: StationRouteBuildRequestScala) = {
    val reqsMap = curlayer.map(rt => rt.currentEnd match {
      case stp: StationScala => rt -> TransferRequest(stp, (req.maxtransfertime - rt.totalTime) avgWalkDist, req.walklayerlimit)
    }).toMap
    val res = TransitDataRetriever.getTransferStations(reqsMap.values.toSeq)
    val rval = curlayer
      .filter(rt => rt.currentEnd.isInstanceOf[StationScala])
      .map(rt => rt -> res(reqsMap(rt))
        .toStream
        .filter(st => rt.currentEnd.distanceTo(st) < (req.delta - rt.totalTime).avgWalkDist && !rt.hasPoint(st))
        .map(st => TransferWalkStep(rt.currentEnd.asInstanceOf[StationScala], st, rt.currentEnd.distanceTo(st).avgWalkTime))
      )
    rval
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
      .map(rt => rt -> PathsRequest(rt.currentEnd.asInstanceOf[StationScala], rt.endTime + TimeDeltaScala.SECOND, req.delta - rt.totalTime - TimeDeltaScala(10000), pathsLimit))
      .toMap

    val pathResults = TransitDataRetriever.getPathsForStation(pathReqs.values.toSeq)

    val arrivablereqs = pathReqs.flatMap({
      case (route, chainreq) => pathResults(chainreq).map(data => data -> buildArrivalReq(route, data, req, layersize))
    })

    val arrivableres = TransitDataRetriever.getArrivableStation(arrivablereqs.values.toSeq)

    val rval = for (rt <- curlayer; startStation <- pathResults(pathReqs(rt))) yield {
      val tstart = startStation.nextArrival(rt.endTime)
      val waittime = rt.endTime timeUntil tstart
      require(waittime > TimeDeltaScala.NULL, s"Got no wait between ${rt.endTime.packedTime}, ${startStation.schedule.map(_.packedTime)}")
      arrivableres(arrivablereqs(startStation))
        .filter(st => !rt.hasPoint(st.station))
        .map(FromDataTransitStep(startStation, _, tstart, waittime))
        .map(rt + _)
    }
    rval.flatten
  }

  @inline
  private def buildArrivalReq(route: RouteScala, chaininfo: StationWithRoute, req: StationRouteBuildRequestScala, curlayer: Int): ArrivableRequest = {
    val trueStart = chaininfo.nextArrival(route.endTime)
    val truedelta = req.delta - route.totalTime - route.endTime.timeUntil(trueStart)
    val limit = if (req.transitlimit == Int.MaxValue) Int.MaxValue else (req.transitlimit * LimitScale / curlayer).toInt
    ArrivableRequest(chaininfo, trueStart, truedelta, limit)
  }
}
