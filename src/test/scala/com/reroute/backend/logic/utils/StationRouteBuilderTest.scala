package com.reroute.backend.logic.utils

import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location.{InputLocation, LocationPoint}
import com.reroute.backend.model.routing.Route
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.helpers.StationDatabaseManager
import org.junit.Assert._
import org.junit.{After, Before, Test}
import org.scalatest.junit.AssertionsForJUnit

class StationRouteBuilderTest extends AssertionsForJUnit {

  @Before
  def initialize(): Unit = {
    StationDatabaseManager.setTest(true)
  }

  @Test
  def testForwardRouteBuilder(): Unit = {
    val req = StationRouteBuildRequestScala(
      start = InputLocation(37.5, -122),
      starttime = TimePoint(0, "GMT"),
      delta = TimeDeltaLimit(total_max = TimeDelta(4 * 60 * 60 * 1000)),
      finallimit = 100,
      stepLimit = 10
    )

    val res = StationRouteBuilder.buildStationRouteList(req)
    assertTrue("Got no base route!", res.exists(rt => rt.currentEnd == rt.start))
    assertTrue(s"Got ${res.size} routes, but expected ${req.finallimit}!", res.lengthCompare(req.finallimit) == 0)
    res.foreach(checkRoute(_, req))
  }

  @Test
  def testBuildFilters(): Unit = {
    val req = StationRouteBuildRequestScala(
      start = InputLocation(37.5, -122),
      starttime = TimePoint(0, "GMT"),
      delta = TimeDeltaLimit(total_max = TimeDelta(4 * 60 * 60 * 1000)),
      waitTime = TimeDeltaLimit(
        min = TimeDelta(60 * 1000)
      ),
      walkDistance = DistanceLimit(
        max = Distance(2.5, DistUnits.KILOMETERS),
        total_max = Distance(10, DistUnits.KILOMETERS),
        total_min = Distance(5, DistUnits.KILOMETERS)
      ),
      finallimit = 100,
      stepLimit = 10
    )
    val res = StationRouteBuilder.buildStationRouteList(req)
    assertTrue("Got no base route!", res.exists(rt => rt.currentEnd == rt.start))
    assertTrue(s"Got ${res.size} routes, but expected ${req.finallimit}!", res.lengthCompare(req.finallimit) == 0)
    res.foreach(checkRoute(_, req))
  }

  @inline
  private def checkRoute(route: Route, req: StationRouteBuildRequestScala): Unit = {
    assertTrue(req.routeValid(route))
    assertTrue(s"Route failed minimums check:\n $route", req.meetsMinimums(route) || route.steps.lengthCompare(1) <= 0)
    assertTrue(route.totalTime <= req.delta.total_max)
    assertTrue(route.walkTime <= req.walkTime.total_max)
    assertTrue(route.travelTime <= req.transitTime.total_max)
    assertTrue(route.steps.lengthCompare(req.stepLimit) <= 0)
    assertTrue(route.start == req.start)
    assertTrue(route.starttime == req.starttime)
    val netspeed = (route.start distanceTo route.currentEnd) / route.totalTime.hours
    assertTrue(s"Speed ${netspeed.in(DistUnits.KILOMETERS)} km/h too low!", netspeed >= LocationPoint.AVG_WALKING_PER_HOUR)
  }

  @After
  def untest(): Unit = {
    StationDatabaseManager.setTest(false)
  }
}
