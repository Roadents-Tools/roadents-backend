package com.reroute.backend.logic.generator

import com.reroute.backend.locations.LocationRetriever
import com.reroute.backend.logic.ApplicationResultScala
import com.reroute.backend.logic.interfaces.LogicCoreScala
import com.reroute.backend.logic.utils.{StationRouteBuildRequestScala, StationRouteBuilderScala}
import com.reroute.backend.model.distance.{Distance, DistanceUnits, DistanceUnitsScala}
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDeltaScala

import scala.collection.JavaConverters._

/**
  * Created by ilan on 7/10/16.
  */
class GeneratorCoreScala extends LogicCoreScala[GeneratorRequest] {

  private final val BAD_DEST = DestinationScala("null", -360, -360, List())

  override def runLogic(request: GeneratorRequest): ApplicationResultScala = {
    val pt = request.start
    val startTime = request.starttime
    val maxTimeDelta = request.totaltime

    //Get the station routes
    val stroutesreq = StationRouteBuildRequestScala(start = pt, starttime = startTime, delta = maxTimeDelta)
    val stationRoutes = StationRouteBuilderScala.buildStationRouteList(stroutesreq)
    printf("Got %d station routes.\n", stationRoutes.size)

    //Get the raw dest routes
    val destRoutes = stationRoutes
      .filter(route => maxTimeDelta >= route.totalTime)
      .flatMap(route => getWalkableDestinations(route.currentEnd, maxTimeDelta - route.totalTime, request.desttype).map(node => route + node))
      .toList
    printf("Got %d -> %d dest routes.\n", stationRoutes.size, destRoutes.size)

    val destToShortest = destRoutes
      .groupBy(_.dest.getOrElse(BAD_DEST))
      .mapValues(_.minBy(rt => rt.totalTime.unixdelta + rt.steps.size))
    val rval = destToShortest.values.toList
    printf("Got %d -> %d filtered routes. Of those, %d are nonzero degree.\n", destRoutes.size, destToShortest.size, rval.count(_.steps.lengthCompare(2) >= 0))

    //Build the output
    ApplicationResultScala.Result(destToShortest.values.toList)
  }

  override val tag: String = "DONUT"

  override def isValid(request: GeneratorRequest): Boolean = {
    request.tag == tag
  }

  def getWalkableDestinations(center: LocationPointScala, maxDelta: TimeDeltaScala, destquery: DestCategory): Seq[RouteStepScala] = {
    LocationRetriever.getLocations(new StartPoint(center.latitude, center.longitude), new Distance(maxDelta.avgWalkDist in DistanceUnitsScala.METERS, DistanceUnits.METERS), new LocationType(destquery.category, destquery.category))
      .asScala
      .map((point: DestinationLocation) => center match {
        case pt: StartScala => FullRouteWalkStep(pt, DestinationScala.fromJava(point), (pt distanceTo DestinationScala.fromJava(point)).avgWalkTime)
        case pt: StationScala => DestinationWalkStep(pt, DestinationScala.fromJava(point), (pt distanceTo DestinationScala.fromJava(point)).avgWalkTime)
      })
  }
}