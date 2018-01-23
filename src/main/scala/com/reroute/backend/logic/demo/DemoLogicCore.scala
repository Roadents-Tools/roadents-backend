package com.reroute.backend.logic.demo


import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.stationroute.{StationRouteGenerator, StationRouteRequest}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing.{GeneralWalkStep, Route, WalkStep}
import com.reroute.backend.model.time.TimeDelta

import scala.collection.{breakOut, mutable}

object DemoLogicCore extends LogicCore[DemoRequest] {
  override val tag: String = "DEMO"

  override def runLogic(request: DemoRequest): ApplicationResult = {
    val numRoutesPer = request.limit / DemoSorter.VALUES.length
    val statreq = StationRouteRequest
      .maxDeltaOnly(request.start, request.starttime, request.maxdelta, request.limit * 2)
      .copy(
        yieldFilter = rtFilter(_, request),
        branchFilter = rtFilter(_, request)
      )
    println(s"DEMO Req: $statreq")
    val shortreq = StationRouteRequest
      .maxDeltaOnly(request.start, request.starttime, Seq(request.maxdelta, 10 * TimeDelta.MINUTE).min, request.limit * 2)
      .copy(
        yieldFilter = rtFilter(_, request),
        branchFilter = rtFilter(_, request)
      )
    println(s"DEMO Req: $shortreq")
    val longRoutes = StationRouteGenerator.buildStationRouteList(statreq)
    val shortRoutes = StationRouteGenerator.buildStationRouteList(shortreq)
    val stationRoutes = shortRoutes ++ longRoutes
    println(s"Got ${stationRoutes.count(isntDumbRoute(_, request).isDefined)} station dumbs.")
    val bestStationRoutes = mutable.Map[String, Seq[Route]]()
    if (stationRoutes.lengthCompare(numRoutesPer * DemoSorter.VALUES.length) >= 0) {
      for (compObj <- DemoSorter.VALUES) {
        val tag = compObj.tag
        val comp = compObj.sorter
        val sortRoutes = stationRoutes.sorted(comp)
        bestStationRoutes.put(tag, sortRoutes.take(numRoutesPer))
      }
    }
    else {
      for (compObj <- DemoSorter.VALUES) {
        val tag = compObj.tag
        val comp = compObj.sorter
        val sortRoutes = stationRoutes.sorted(comp)
        bestStationRoutes.put(tag, sortRoutes)
      }
    }
    val stationRoutesToReqs: Map[Route, LocationsRequest] = bestStationRoutes.values.flatten.map(rt => rt -> {
      LocationsRequest(rt.currentEnd, Seq(allowedWalkTime(rt, request), request.maxdelta - rt.totalTime).min, request.endquery)
    })(breakOut)
    val allResults = LocationRetriever.getLocations(stationRoutesToReqs.values.toSeq)
    val possibleDests = allResults.flatMap(_._2).toSeq
    var destRoutes: Seq[Route] = bestStationRoutes.values
      .flatten
      .flatMap(rt => buildDestRoutes(rt, possibleDests))
      .foldLeft(Map.empty[Long, Route])((map, rt) => {
        val loctag = locationTag(rt.currentEnd)
        val existing = map.get(loctag)
        if (existing.isDefined && existing.get.totalTime <= rt.totalTime) map
        else map + (loctag -> rt)
      })
      .values
      .toSeq
    if (destRoutes.isEmpty) {
      val base = new Route(request.start, request.starttime)
      val locreq = LocationsRequest(request.start, request.maxdelta, request.endquery)
      destRoutes = buildDestRoutes(base, LocationRetriever.getLocations(Seq(locreq)).apply(locreq))
    }
    val bestDestRoutes = mutable.Map[String, Seq[Route]]()
    for (compObj <- DemoSorter.VALUES) {
      val tag = compObj.tag
      val comp = compObj.sorter
      val sortRoutes = new mutable.MutableList[Route]()
      sortRoutes ++= destRoutes.sorted(comp)
      while (sortRoutes.lengthCompare(numRoutesPer) < 0) {
        sortRoutes += new Route(sortRoutes.head.start, sortRoutes.head.starttime)
      }
      bestDestRoutes.put(tag, sortRoutes.take(numRoutesPer))
    }
    val rval = DemoSorter.VALUES
      .map(_.tag)
      .flatMap(bestDestRoutes(_))
      .toSeq
    println(s"PITCH RVAL: ${rval.map(_.currentEnd).distinct.size}")
    val dafuq = rval
      .map(isntDumbRoute(_, request))
      .filter(_.isDefined)
    println(s"PITCH Dafuq: ${dafuq.groupBy(_.get).mapValues(_.size)}")
    ApplicationResult.Result(rval)
  }

  private def buildDestRoutes(rt: Route, dests: Seq[ReturnedLocation]): Seq[Route] = {
    val basicRoute = new Route(rt.start, rt.starttime)
    val basicRoutes = dests
      .map(dest => GeneralWalkStep(rt.start, dest, dest.distanceTo(rt.start).avgWalkTime))
      .map(basicRoute + _)
    val complexRoutes = dests.map(dest => {
      if (rt.steps.isEmpty) {
        GeneralWalkStep(rt.start, dest, dest.distanceTo(rt.currentEnd).avgWalkTime)
      } else {
        GeneralWalkStep(rt.currentEnd.asInstanceOf[Station], dest, dest.distanceTo(rt.currentEnd).avgWalkTime)
      }
    }).map(rt + _)
    basicRoutes ++ complexRoutes
  }

  private def rtFilter(rt: Route,
                       req: DemoRequest): Boolean = isntDumbRoute(rt, req).isEmpty && isGoodLookingRoute(rt, req)

  private def isntDumbRoute(rt: Route, req: DemoRequest): Option[String] = {
    if (rt.totalTime > req.maxdelta) Some("MaxDelta")
    else if (rt.walkDistance > rt.distance) Some("WalkDist")
    else if (rt.totalDistance > LocationPoint.MAX_TRANSIT_PER_HOUR * rt.totalTime.hours) Some("Flash")
    else if (rt.distance.avgWalkTime < rt.totalTime) Some("Sloth")
    else if (rt.totalDistance / rt.totalTime.hours < LocationPoint.AVG_WALKING_PER_HOUR) Some("Walkies")
    else if (rt.steps.map(_.endpt).forall(pt => rt.steps.map(_.endpt).count(_.overlaps(pt)) != 1)) Some("Count")
    else None
  }

  private def isGoodLookingRoute(rt: Route, request: DemoRequest): Boolean = {
    rt.walkTime <= allowedWalkTime(rt, request)
  }

  private def allowedWalkTime(rt: Route, request: DemoRequest): TimeDelta = {
    val totalWalks = 1 + rt.steps.count(_.isInstanceOf[WalkStep])
    val maxPercent = (1 to totalWalks).map(inp => 1.0 / Math.pow(2, inp)).sum
    maxPercent * request.maxdelta
  }

  override def isValid(request: DemoRequest): Boolean = {
    request.tag == tag && request.limit % DemoSorter.VALUES.length == 0
  }

  @inline
  private def locationTag(loc: LocationPoint): Long = {
    loc.latitude.hashCode() << 31 + loc.longitude.hashCode()
  }
}
