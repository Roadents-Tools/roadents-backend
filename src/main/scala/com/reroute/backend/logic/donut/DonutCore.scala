package com.reroute.backend.logic.donut

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.utils.{StationRouteBuildRequestScala, StationRouteBuilder, TimeDeltaLimit}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDelta

import scala.collection.breakOut
import scala.util.{Success, Try}

/**
  * Created by ilan on 7/10/16.
  */
object DonutCore extends LogicCore[DonutRequest] {

  private final val BAD_DEST = ReturnedLocation("null", -360, -360, List())

  override def runLogic(request: DonutRequest): ApplicationResult = Try {

    //Get the station routes
    val stroutesreq = StationRouteBuildRequestScala(
      start = request.start,
      starttime = request.starttime,
      delta = TimeDeltaLimit(total_max = request.totaltime),
      finallimit = request.limit
    )
    val stationRoutes = StationRouteBuilder.buildStationRouteList(stroutesreq)
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
      .filter(route => request.totaltime >= route.totalTime)
      .flatMap(route => destReqs.get(route).flatMap(destRes.get).map(buildDestRoutes(route, _)).getOrElse(Seq.empty))
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