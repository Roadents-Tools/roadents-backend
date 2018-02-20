package com.reroute.backend.logic.revdonut

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.revstationroute.{RevStationQueryBuilder, RevStationRouteGenerator, RevStationRouteRequest}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDelta
import com.typesafe.scalalogging.Logger

import scala.collection.breakOut
import scala.util.{Success, Try}

/**
 * Created by ilan on 7/10/16.
 */
object RevDonutCore extends LogicCore[RevDonutRequest] {

  private final val BRANCH_MODIFIER = 5
  private final val YIELD_MODIFIER = 2
  private final val WALK_MODIFIER = 3.0
  private final val PATHS_MODIFIER = 10.0
  private final val ARRIVABLE_MODIFIER = 10.0

  private final val logger = Logger[RevDonutCore.type]

  override def runLogic(request: RevDonutRequest): ApplicationResult = Try {

    //Get the station routes
    logger.info(s"Got revreq $request.")
    val yieldFilter: Route => Boolean = (route: Route) => {
      val totdt = route.totalTime >= request.totaltime
      val notWalkEnd = !route.steps.headOption.exists(_.isInstanceOf[WalkStep])
      val totwalkdt = route.walkTime >= request.totalwalktime
      val totwaitdt = route.waitTime >= request.totalwalktime
      val stepcnt = route.steps.lengthCompare(request.steps - 1) <= 0
      val stepvalid = route.steps.forall({
        case stp: WalkStep => stp.totaltime > request.maxwalktime
        case stp: TransitStep => stp.waittime > request.maxwaittime && stp.waittime < request.minwaittime
      })
      val mindistvalid = route.distance + request.effectiveWalkLeft(route).avgWalkDist > request.mindist
      totdt && notWalkEnd && totwalkdt && totwaitdt && stepcnt && stepvalid && mindistvalid
    }
    val branchFilter: Route => Boolean = (route: Route) => {
      val totdt = route.totalTime > request.totaltime
      val totwalkdt = route.walkTime > request.totalwalktime
      val totwaitdt = request.totalwaittime - route.waitTime < request.minwaittime
      val stepcnt = route.steps.lengthCompare(request.steps - 2) <= 0
      val stepvalid = route.steps.forall({
        case stp: WalkStep => stp.totaltime > request.maxwalktime
        case stp: TransitStep => stp.waittime > request.maxwaittime && stp.waittime < request.minwaittime
      })
      totdt && totwalkdt && totwaitdt && stepcnt && stepvalid
    }
    val stroutesreq = RevStationRouteRequest(
      request.start,
      request.starttime,
      request.limit * YIELD_MODIFIER,
      request.limit * BRANCH_MODIFIER,
      yieldFilter,
      branchFilter,
      RevStationQueryBuilder.standardBuilder(request, WALK_MODIFIER, PATHS_MODIFIER, ARRIVABLE_MODIFIER)
    )

    val stationRoutes = RevStationRouteGenerator.buildStationRouteList(stroutesreq)
    logger.info(s"Got ${stationRoutes.size} station routes.\n")

    //Get the raw dest routes
    val destReqs: Map[Route, LocationsRequest] = stationRoutes
      .filter(route => request.totaltime < route.totalTime)
      .map(route => route -> LocationsRequest(
        route.currentEnd,
        request.effectiveWalkLeft(route).abs,
        request.desttype
      ))(breakOut)
    val destRes = LocationRetriever.getLocations(destReqs.values.toSeq)

    val destRoutes = stationRoutes
      .flatMap(route => destReqs.get(route).flatMap(destRes.get).map(buildDestRoutes(route, _)).getOrElse(Seq.empty))
      .map(_.reverse(request.minwaittime))
      .filter(request.meetsRequest)
      .toList
    logger.info(s"Got ${stationRoutes.size} -> ${destRoutes.size} dest routes.\n")

    val destToShortest = destRoutes
      .groupBy(_.start)
      .mapValues(_.minBy(rt => rt.totalTime.unixdelta + rt.steps.size))
    val rval = destToShortest.values.toStream.sortBy(_.totalTime).take(request.limit)
    logger.info(s"Got ${destRoutes.size} -> ${rval.size} filtered routes. Of those, ${rval.count(_.steps.lengthCompare(1) > 0)} are nonzero degree.\n")
    rval.foreach(rt => assert(request.meetsRequest(rt), "Error building malformed route."))

    //Build the output
    ApplicationResult.Result(rval)
  } recoverWith {
    case e: Throwable => Success(ApplicationResult.Error(List(e.getMessage)))
  } getOrElse {
    ApplicationResult.Error(List("An unknown error occurred."))
  }

  override val tag: String = "DONUT"

  override def isValid(request: RevDonutRequest): Boolean = {
    request.tag == tag && request.totaltime > TimeDelta.NULL
  }

  private def buildDestRoutes(route: Route, dests: Seq[ReturnedLocation]) = {
    val end = route.currentEnd
    dests
      .map(d => GeneralWalkStep(end, d, end.distanceTo(d).avgWalkTime * -1))
      .map(route + _)
  }

}

