package com.reroute.backend.logic.donut

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.stationroute.{StationQueryGenerator, StationRouteGenerator, StationRouteRequest}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDelta
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

import scala.collection.breakOut
import scala.util.{Success, Try}

/**
 * Created by ilan on 7/10/16.
 */
object DonutCore extends LogicCore[DonutRequest] {

  private final val BRANCH_MODIFIER = 5
  private final val YIELD_MODIFIER = 2
  private final val WALK_MODIFIER = 3.0
  private final val PATHS_MODIFIER = 10.0
  private final val ARRIVABLE_MODIFIER = 10.0

  private final val BAD_DEST = ReturnedLocation("null", -360, -360, List())

  override def runLogic(request: DonutRequest): ApplicationResult = Try {

    //Get the station routes
    println(s"Donut req got walk times: ${request.totaltime.hours}, ${request.totalwalktime.hours}, ${request.maxwalktime.hours}")
    val queryGenerator = StationQueryGenerator(
      genStartQuery = start => (
        start,
        Seq(request.maxwalktime, request.totalwalktime, request.totaltime).min.avgWalkDist,
        (request.limit * WALK_MODIFIER).toInt
      ),
      genTransferQuery = (rt, curlayer) => TransferRequest(
        rt.currentEnd.asInstanceOf[Station],
        request.effectiveWalkLeft(rt).avgWalkDist,
        (request.limit / curlayer * WALK_MODIFIER).toInt
      ),
      genArrivableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.nextDeparture(rt.endTime),
        data.nextDeparture(rt.endTime).timeUntil(request.endTime),
        (request.limit / curlayer * ARRIVABLE_MODIFIER).toInt
      ),
      genPathsQuery = (rt, curlayer) => PathsRequest(
        rt.currentEnd.asInstanceOf[Station],
        rt.endTime,
        request.effectiveWaitLeft(rt),
        (request.limit / curlayer * PATHS_MODIFIER).toInt
      )
    )
    val yieldFilter: Route => Boolean = (route: Route) => {
      val totdt = route.totalTime <= request.totaltime
      val notWalkEnd = !route.steps.headOption.exists(_.isInstanceOf[WalkStep])
      val totwalkdt = route.walkTime <= request.totalwalktime
      val totwaitdt = route.waitTime <= request.totalwalktime
      val stepcnt = route.steps.lengthCompare(request.steps - 1) <= 0
      val stepvalid = route.steps.forall({
        case stp: WalkStep => stp.totaltime < request.maxwalktime
        case stp: TransitStep => stp.waittime < request.maxwaittime && stp.waittime > request.minwaittime
      })
      val mindistvalid = route.distance + request.effectiveWalkLeft(route).avgWalkDist > request.mindist
      totdt && notWalkEnd && totwalkdt && totwaitdt && stepcnt && stepvalid && mindistvalid
    }
    val branchFilter: Route => Boolean = (route: Route) => {
      val totdt = route.totalTime < request.totaltime
      val totwalkdt = route.walkTime < request.totalwalktime
      val totwaitdt = request.totalwaittime - route.waitTime > request.minwaittime
      val stepcnt = route.steps.lengthCompare(request.steps - 2) <= 0
      val stepvalid = route.steps.forall({
        case stp: WalkStep => stp.totaltime < request.maxwalktime
        case stp: TransitStep => stp.waittime < request.maxwaittime && stp.waittime > request.minwaittime
      })
      if (!totdt) println(s"A: ${route.totalTime.hours} VS ${request.totaltime.hours}")
      if (!totwalkdt) println(s"B: ${route.walkTime.hours} VS ${request.totalwalktime.hours}")
      if (!totwaitdt) println(s"C: ${route.waitTime.seconds} VS ${request.totalwaittime.seconds}")
      if (!stepcnt) println(s"D: ${route.steps.size} VS ${request.steps}")
      if (!stepvalid) println(s"E: ${route.steps.map(_.totaltime.seconds)}")
      totdt && totwalkdt && totwaitdt && stepcnt && stepvalid
    }
    val stroutesreq = StationRouteRequest(
      request.start,
      request.starttime,
      request.limit * YIELD_MODIFIER,
      request.limit * BRANCH_MODIFIER,
      yieldFilter,
      branchFilter,
      queryGenerator
    )

    val stationRoutes = StationRouteGenerator.buildStationRouteList(stroutesreq)
    printf("Got %d station routes.\n", stationRoutes.size)

    //Get the raw dest routes
    val destReqs: Map[Route, LocationsRequest] = stationRoutes
      .filter(route => request.totaltime > route.totalTime)
      .map(route => route -> LocationsRequest(
        route.currentEnd,
        Seq(request.totaltime - route.totalTime, request.totalwalktime - route.walkTime, request.maxwalktime).min,
        request.desttype
      ))(breakOut)
    val destRes = LocationRetriever.getLocations(destReqs.values.toSeq)

    val destRoutes = stationRoutes
      .flatMap(route => destReqs.get(route).flatMap(destRes.get).map(buildDestRoutes(route, _)).getOrElse(Seq.empty))
      .filter(request.meetsRequest)
      .toList
    printf("Got %d -> %d dest routes.\n", stationRoutes.size, destRoutes.size)

    val destToShortest = destRoutes
      .groupBy(_.dest.getOrElse(BAD_DEST))
      .mapValues(_.minBy(rt => rt.totalTime.unixdelta + rt.steps.size))
    val rval = destToShortest.values.toStream.sortBy(_.totalTime).take(request.limit)
    printf("Got %d -> %d filtered routes. Of those, %d are nonzero degree.\n", destRoutes.size, rval.size, rval.count(_.steps.lengthCompare(2) >= 0))
    rval.foreach(rt => assert(request.meetsRequest(rt), "Error building malformed route."))

    //Build the output
    ApplicationResult.Result(rval)
  } recoverWith {
    case e: Throwable => Success(ApplicationResult.Error(List(e.getMessage)))
  } getOrElse {
    ApplicationResult.Error(List("An unknown error occurred."))
  }

  override val tag: String = "DONUT"

  override def isValid(request: DonutRequest): Boolean = {
    request.tag == tag && request.totaltime > TimeDelta.NULL
  }

  private def buildDestRoutes(route: Route, dests: Seq[ReturnedLocation]) = {
    val steps = route.currentEnd match {
      case pt: InputLocation => dests.map(d => FullRouteWalkStep(pt, d, pt.distanceTo(d).avgWalkTime))
      case pt: Station => dests.map(d => DestinationWalkStep(pt, d, pt.distanceTo(d).avgWalkTime))
    }
    steps.map(route + _)
  }

}