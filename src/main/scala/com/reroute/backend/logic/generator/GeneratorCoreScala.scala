package com.reroute.backend.logic.generator

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResultScala
import com.reroute.backend.logic.interfaces.LogicCoreScala
import com.reroute.backend.logic.utils.{StationRouteBuildRequestScala, StationRouteBuilderScala, TimeDeltaLimit}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDeltaScala

import scala.collection.breakOut
import scala.util.{Success, Try}

/**
  * Created by ilan on 7/10/16.
  */
object GeneratorCoreScala extends LogicCoreScala[GeneratorRequest] {

  private final val BAD_DEST = DestinationScala("null", -360, -360, List())

  override def runLogic(request: GeneratorRequest): ApplicationResultScala = Try {

    //Get the station routes
    val stroutesreq = StationRouteBuildRequestScala(
      start = request.start,
      starttime = request.starttime,
      delta = TimeDeltaLimit(total_max = request.totaltime),
      finallimit = request.limit
    )
    val stationRoutes = StationRouteBuilderScala.buildStationRouteList(stroutesreq)
    printf("Got %d station routes.\n", stationRoutes.size)

    //Get the raw dest routes
    val destReqs: Map[RouteScala, LocationsRequest] = stationRoutes
      .filter(route => request.totaltime > route.totalTime)
      .map(route => route -> LocationsRequest(
        route.currentEnd,
        Seq(request.totaltime - route.totalTime, request.totalwalktime - route.walkTime, request.maxwalktime).min,
        request.desttype
      ))(breakOut)
    val destRes = LocationRetriever.getLocations(destReqs.values.toSeq)

    val destRoutes = stationRoutes
      .filter(route => request.totaltime >= route.totalTime)
      .flatMap(route => buildDestRoutes(route, destRes(destReqs(route))))
      .toList
    printf("Got %d -> %d dest routes.\n", stationRoutes.size, destRoutes.size)

    val destToShortest = destRoutes
      .groupBy(_.dest.getOrElse(BAD_DEST))
      .mapValues(_.minBy(rt => rt.totalTime.unixdelta + rt.steps.size))
    val rval = destToShortest.values.toStream.sortBy(_.totalTime).take(request.limit)
    printf("Got %d -> %d filtered routes. Of those, %d are nonzero degree.\n", destRoutes.size, rval.size, rval.count(_.steps.lengthCompare(2) >= 0))

    //Build the output
    ApplicationResultScala.Result(rval)
  } recoverWith {
    case e: Throwable => Success(ApplicationResultScala.Error(List(e.getMessage)))
  } getOrElse {
    ApplicationResultScala.Error(List("An unknown error occurred."))
  }

  override val tag: String = "DONUT"

  override def isValid(request: GeneratorRequest): Boolean = {
    request.tag == tag && request.totaltime > TimeDeltaScala.NULL
  }

  private def buildDestRoutes(route: RouteScala, dests: Seq[DestinationScala]) = {
    val steps = route.currentEnd match {
      case pt: StartScala => dests.map(d => FullRouteWalkStep(pt, d, pt.distanceTo(d).avgWalkTime))
      case pt: StationScala => dests.map(d => DestinationWalkStep(pt, d, pt.distanceTo(d).avgWalkTime))
    }
    steps.map(route + _)
  }

}