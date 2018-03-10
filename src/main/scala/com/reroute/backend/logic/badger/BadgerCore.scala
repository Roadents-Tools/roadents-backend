package com.reroute.backend.logic.badger

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.revstationroute.{RevStationQueryBuilder, RevStationRouteGenerator, RevStationRouteRequest}
import com.reroute.backend.logic.stationroute.{StationQueryBuilder, StationRouteGenerator, StationRouteRequest}
import com.reroute.backend.model.distance.{CircleArea, DistUnits, Distance}
import com.reroute.backend.model.routing._
import com.typesafe.scalalogging.Logger

import scala.collection.breakOut
import scala.util.{Success, Try}

object BadgerCore extends LogicCore[BadgerRequest] {
  private final val BRANCH_MODIFIER = 5
  private final val YIELD_MODIFIER = 2
  private final val WALK_MODIFIER = 3.0
  private final val PATHS_MODIFIER = 10.0
  private final val ARRIVABLE_MODIFIER = 10.0
  private final val MAX_DISTANCE_PER_HOUR = Distance(60, DistUnits.KILOMETERS)

  private final val logger = Logger[BadgerCore.type]

  override val tag: String = "BADGER"

  override def runLogic(request: BadgerRequest): ApplicationResult = Try {
    val posRoutes = buildPosRoutes(request)
    logger.info(s"Got ${posRoutes.size} pos routes.")
    val negRoutes = buildNegRoutes(request, posRoutes.values.toSeq)
    logger.info(s"Got ${negRoutes.size} neg routes. ")
    val allPairs = for (posrt <- posRoutes; negrt <- negRoutes) yield {
      posrt._1 -> negrt._1 -> posrt._2.intersection(negrt._2)
    }
    val usablePairs = allPairs
      .mapValues(raw => raw.copy(range = raw.range - request.pauseTime.avgWalkDist))
      .filter(_._2.range >= Distance.NULL)
    logger.info(s"Got ${usablePairs.size} usable pairs. ")

    val locReq = usablePairs.values
      .toStream
      .map(area => LocationsRequest(area.center, area.range.avgWalkTime, request.desttype))
    val locRes = LocationRetriever.getLocations(locReq)
    val rvalLocs = locRes.values.toStream.flatten.take(request.limit)
    val rval = for (loc <- rvalLocs) yield {
      val components = usablePairs.minBy({
        case ((posrt, negrt), _) => posrt.currentEnd.distanceTo(loc) + negrt.currentEnd.distanceTo(loc)
      })
      val (posbase, negbase) = components._1
      val forwardWalk = GeneralWalkStep(posbase.currentEnd, loc, posbase.currentEnd.distanceTo(loc).avgWalkTime)
      val revWalk = GeneralWalkStep(negbase.currentEnd, loc, negbase.currentEnd.distanceTo(loc).avgWalkTime * -1)

      val fullPos = posbase + forwardWalk
      val fullNeg = negbase + revWalk
      val revedNeg = fullNeg.reverse(request.minwaittime)

      val pitstopTime = fullPos.endTime.timeUntil(revedNeg.starttime)

      val pitstopstep = PitstopStep(loc, pitstopTime)
      fullPos + pitstopstep ++ revedNeg.steps
    }
    ApplicationResult.Result(rval)
  } recoverWith {
    case e: Throwable => Success(ApplicationResult.Error(List(e.getMessage)))
  } getOrElse {
    ApplicationResult.Error(List("An unknown error occurred."))
  }

  private def buildPosRoutes(request: BadgerRequest): Map[Route, CircleArea] = {
    val queryGenerator = StationQueryBuilder.standardBuilder(request, WALK_MODIFIER, PATHS_MODIFIER, ARRIVABLE_MODIFIER)
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
      val isPossible = route.distance <= MAX_DISTANCE_PER_HOUR * (request.totaltime - request.pauseTime).hours
      totdt && notWalkEnd && totwalkdt && totwaitdt && stepcnt && stepvalid && mindistvalid && isPossible
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
      val isPossible = route.distance <= MAX_DISTANCE_PER_HOUR * (request.totaltime - request.pauseTime).hours
      totdt && totwalkdt && totwaitdt && stepcnt && stepvalid && isPossible
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

    StationRouteGenerator.buildStationRouteList(stroutesreq)
      .map(rt => rt -> CircleArea(rt.currentEnd, request.effectiveWalkLeft(rt).avgWalkDist))(breakOut)
  }

  private def buildNegRoutes(request: BadgerRequest, validAreas: Seq[CircleArea]): Map[Route, CircleArea] = {
    val queryGenerator = RevStationQueryBuilder.standardBuilder(request)
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
      val walkableEnd = CircleArea(route.currentEnd, request.effectiveWalkLeft(route).avgWalkDist)
      val hasOverlap = validAreas.exists(_.intersects(walkableEnd))
      totdt && notWalkEnd && totwalkdt && totwaitdt && stepcnt && stepvalid && mindistvalid && hasOverlap
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
      queryGenerator
    )

    RevStationRouteGenerator.buildStationRouteList(stroutesreq)
      .map(rt => rt -> CircleArea(rt.currentEnd, request.effectiveWalkLeft(rt).avgWalkDist))(breakOut)
  }
}
