package com.reroute.backend.logic.stationroute

import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location.{InputLocation, LocationPoint}
import com.reroute.backend.model.routing.{Route, TransitStep, WalkStep}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.helpers.StationDatabaseManager
import org.junit.Assert._
import org.junit.{After, Before, Test}
import org.scalatest.junit.AssertionsForJUnit

class StationRouteGeneratorTest extends AssertionsForJUnit {

  @Before
  def initialize(): Unit = {
    StationDatabaseManager.setTest(true)
  }

  @Test
  def testForwardRouteBuilder(): Unit = {
    val start = InputLocation(37.5, -122)
    val starttime = TimePoint(0, "GMT")
    val maxDelta = 4 * TimeDelta.HOUR
    val resSize = 100
    val req = StationRouteRequest.maxDeltaOnly(
      start = start,
      starttime = starttime,
      maxdelta = maxDelta,
      limit = resSize
    )

    val res = StationRouteGenerator.buildStationRouteList(req)
    assertTrue("Got no base route!", res.exists(rt => rt.currentEnd == rt.start))
    assertTrue(s"Got ${res.size} routes, but expected $resSize!", res.lengthCompare(resSize) == 0)
    for (route <- res) {
      assertTrue("Got bad initials!", route.starttime == starttime && route.start == start)
      val maxWalkSpeed = if (route.steps.isEmpty) Distance.NULL else route.steps.map({
        case stp: WalkStep => stp.walkdistance / stp.totaltime.hours
        case _ => Distance.NULL
      }).max
      assertTrue(s"Got fast walking: $maxWalkSpeed vs ${LocationPoint.AVG_WALKING_PER_HOUR}", maxWalkSpeed <= LocationPoint.AVG_WALKING_PER_HOUR + Distance.ERROR_MARGIN)
      val maxTransitSpeed = if (route.steps.isEmpty) Distance.NULL else route.steps.map({
        case stp: TransitStep =>
          if (stp.traveltime <= TimeDelta.NULL) Distance(Long.MaxValue - 10)
          else stp.startpt.distanceTo(stp.endpt) / stp.traveltime.hours
        case _ => Distance.NULL
      }).max
      assertTrue(s"Got fast transit: $maxTransitSpeed vs ${LocationPoint.MAX_TRANSIT_PER_HOUR}", maxTransitSpeed <= LocationPoint.MAX_TRANSIT_PER_HOUR + Distance.ERROR_MARGIN)
      assertTrue("Got route over time!", route.totalTime <= maxDelta)
    }
  }

  @Test
  def testBuildFilters(): Unit = {
    val start = InputLocation(37.5, -122)
    val starttime = TimePoint(0, "GMT")
    val resSize = 100
    val maxDelta = 4 * TimeDelta.HOUR
    val maxWalk = Distance(10, DistUnits.KILOMETERS)
    val minWalk = Distance(5, DistUnits.KILOMETERS)
    val maxStepWalk = Distance(2.5, DistUnits.KILOMETERS)
    val minStepWait = 60 * TimeDelta.SECOND
    val maxSteps = 10
    val req = StationRouteRequest(
      start = start,
      starttime = starttime,
      yieldLimit = resSize,
      branchLimit = resSize * 3,
      yieldFilter = rt => {
        val stepCountWork = rt.steps.lengthCompare(maxSteps) <= 0
        val totalTimeWorks = rt.totalTime <= maxDelta
        val walkWorksMax = rt.walkTime.avgWalkDist <= maxWalk
        val walkWorksMin = rt.walkTime.avgWalkDist >= minWalk
        val walkStepMaxWorks = rt.steps.forall({
          case stp: WalkStep => stp.walkdistance <= maxStepWalk
          case _ => true
        })
        val waitStepMinWorks = rt.steps.forall({
          case stp: TransitStep => stp.waittime >= minStepWait
          case _ => true
        })
        val rval = stepCountWork && totalTimeWorks && walkWorksMax && walkWorksMin && walkStepMaxWorks && waitStepMinWorks
        rval
      },
      branchFilter = rt => {
        val rval = rt.steps.lengthCompare(maxSteps) <= 0 &&
          rt.totalTime <= maxDelta &&
          rt.walkTime.avgWalkDist <= maxWalk &&
          rt.steps.forall({
            case stp: WalkStep => stp.walkdistance <= maxStepWalk
            case stp: TransitStep => stp.waittime >= minStepWait
          })
        if (!rval) {
        }
        rval
      },
      queryGenerator = StationQueryBuilder.simpleBuilder(4 * TimeDelta.HOUR, resSize)
    )

    val res = StationRouteGenerator.buildStationRouteList(req)
    assertTrue(s"Got ${res.size} routes, but expected $resSize!", res.lengthCompare(resSize) == 0)
    for (route <- res) {
      assertTrue("Got bad initials!", route.starttime == starttime && route.start == start)
      assertTrue("Got fast walking!", route.steps.forall({
        case stp: WalkStep => LocationPoint.AVG_WALKING_PER_HOUR + Distance.ERROR_MARGIN >= stp.walkdistance / stp.totaltime.hours
        case _ => true
      }))
      assertTrue("Got fast transit!", route.steps.forall({
        case stp: TransitStep => stp.traveltime > TimeDelta.NULL && stp.startpt.distanceTo(stp.endpt) / stp.traveltime.hours < LocationPoint.MAX_TRANSIT_PER_HOUR
        case _ => true
      }))
      assertTrue("Got route over time!", route.totalTime <= maxDelta)
      assertTrue("Walked too much!", route.walkTime.avgWalkDist <= maxWalk)
      assertTrue("Walked too little!", route.walkTime.avgWalkDist >= minWalk)
      assertTrue("Walked too much at once!", route.steps.forall({
        case stp: WalkStep => stp.walkdistance <= maxStepWalk
        case _ => true
      }))
      assertTrue("Waited too little!", route.steps.forall({
        case stp: TransitStep => stp.waittime >= minStepWait
        case _ => true
      }))
      assertTrue("Got too many steps!", route.steps.lengthCompare(maxSteps) <= 0)
    }
  }

  @inline
  def preprintRoute(rt: Route): String = {
    if (rt.steps.isEmpty)
      s"""BASE ROUTE"""
    else
      s"""steps: ${rt.steps.size},
       dt: ${rt.totalTime.hours},
       walkdt: ${rt.walkTime.hours},
       walkavgdx: ${rt.walkTime.avgWalkDist.in(DistUnits.KILOMETERS)},
       maxwalkdx: ${rt.steps.map({ case stp: WalkStep => stp.walkdistance case _ => Distance.NULL }).max.in(DistUnits.KILOMETERS)},
       minwaitdt: ${rt.steps.map({ case stp: TransitStep => stp.waittime.seconds case _ => Long.MaxValue }).min}"""
  }

  @After
  def untest(): Unit = {
    StationDatabaseManager.setTest(false)
  }
}
