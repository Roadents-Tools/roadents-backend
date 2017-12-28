package com.reroute.backend.logic.generator

import com.reroute.backend.locations.LocationRetriever
import com.reroute.backend.logic.ApplicationResultScala
import com.reroute.backend.logic.interfaces.LogicCoreScala
import com.reroute.backend.logic.utils.{StationRouteBuildRequestScala, StationRouteBuilderScala, TimeDeltaLimit}
import com.reroute.backend.model.distance.{Distance, DistanceUnits, DistanceUnitsScala}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDeltaScala

import scala.collection.JavaConverters._
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
    val destRoutes = stationRoutes
      .filter(route => request.totaltime >= route.totalTime)
      .flatMap(route => getDestinationRoutes(route, request))
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

  def getWalkableDestinations(center: LocationPointScala, maxDelta: TimeDeltaScala, destquery: DestCategory): Seq[RouteStepScala] = {
    LocationRetriever.getLocations(new StartPoint(center.latitude, center.longitude), new Distance(maxDelta.avgWalkDist in DistanceUnitsScala.METERS, DistanceUnits.METERS), new LocationType(destquery.category, destquery.category))
      .asScala
      .map(point => center match {
        case pt: StartScala => FullRouteWalkStep(pt, DestinationScala.fromJava(point), (pt distanceTo DestinationScala.fromJava(point)).avgWalkTime)
        case pt: StationScala => DestinationWalkStep(pt, DestinationScala.fromJava(point), (pt distanceTo DestinationScala.fromJava(point)).avgWalkTime)
      })
  }

  def getDestinationRoutes(route: RouteScala, generatorRequest: GeneratorRequest): Seq[RouteScala] = {
    val usableTime = Seq(generatorRequest.maxwalktime, generatorRequest.totalwalktime - route.walkTime, generatorRequest.totaltime - route.totalTime)
      .filter(_ > TimeDeltaScala.NULL)
      .min
    getWalkableDestinations(route.currentEnd, usableTime, generatorRequest.desttype).map(node => route + node)

  }
}