package com.reroute.backend.logic.generator

import com.reroute.backend.model.location.{DestCategory, StartScala}
import com.reroute.backend.model.routing.FullRouteWalkStep
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.helpers.StationDatabaseManager
import org.junit.Assert._
import org.junit.{After, Before, Test}
import org.scalatest.junit.AssertionsForJUnit

class GeneratorCoreScalaTest extends AssertionsForJUnit {

  @Before
  def init(): Unit = {
    StationDatabaseManager.setTest(true)
  }

  @Test
  def testGenerator(): Unit = {
    val maxDelta = TimeDeltaScala(10 * 60 * 1000)
    val req = GeneratorRequest(
      startPoint = StartScala(37.5, -122),
      inpstarttime = Some(TimePointScala(0, "GMT")),
      maxDelta = maxDelta,
      desttype = DestCategory("TEST")
    )

    val res = new GeneratorCoreScala().runLogic(req)

    assertTrue(res.routes.exists(_.steps.exists(_.isInstanceOf[FullRouteWalkStep])))
    assertTrue(res.routes.exists(_.steps.lengthCompare(1) == 0))
    assertTrue(res.routes.forall(_.steps.size % 2 == 1))
  }

  @After
  def teardown(): Unit = {
    StationDatabaseManager.setTest(false)
  }
}
