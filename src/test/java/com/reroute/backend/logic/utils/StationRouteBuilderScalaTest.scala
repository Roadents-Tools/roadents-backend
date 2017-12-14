package com.reroute.backend.logic.utils

import com.reroute.backend.model.distance.DistanceUnitsScala
import com.reroute.backend.model.location.{LocationPointScala, StartScala}
import com.reroute.backend.model.routing.RouteScala
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class StationRouteBuilderScalaTest extends AssertionsForJUnit {

  @Test
  def testForwardRouteBuilder(): Unit = {
    val maxDelta = TimeDeltaScala(4 * 60 * 60 * 1000)
    val req = StationRouteBuildRequestScala(
      start = StartScala(37.5, -122),
      starttime = TimePointScala(0, "GMT"),
      delta = maxDelta,
      finallimit = 100,
      stepcount = 10
    )

    val res = StationRouteBuilderScala.buildStationRouteList(req)
    assertTrue("Got no base route!", res.exists(rt => rt.currentEnd == rt.start))
    assertTrue(s"Got ${res.size} routes, but expected ${req.finallimit}!", res.lengthCompare(req.finallimit) == 0)
    res.foreach(checkRoute(_, req))
  }

  @inline
  private def checkRoute(route: RouteScala, req: StationRouteBuildRequestScala): Unit = {
    assertTrue(route.totalTime <= req.delta)
    assertTrue(route.walkTime <= req.totalwalktime)
    assertTrue(route.travelTime <= req.totaltransittime)
    assertTrue(route.steps.lengthCompare(req.stepcount) <= 0)
    assertTrue(route.start == req.start)
    assertTrue(route.starttime == req.starttime)
    val netspeed = (route.start distanceTo route.currentEnd) / route.totalTime.hours
    assertTrue(s"Speed ${netspeed.in(DistanceUnitsScala.KILOMETERS)} km/h too low!", netspeed >= LocationPointScala.AVG_WALKING_PER_HOUR)
  }
}
