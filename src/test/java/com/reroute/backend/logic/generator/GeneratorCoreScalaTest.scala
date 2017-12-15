package com.reroute.backend.logic.generator

import com.reroute.backend.model.location.{DestCategory, StartScala}
import com.reroute.backend.model.routing.FullRouteWalkStep
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class GeneratorCoreScalaTest extends AssertionsForJUnit {

  @Test
  def testGenerator(): Unit = {
    val maxDelta = TimeDeltaScala(10 * 60 * 1000)
    val req = GeneratorRequest(
      start = StartScala(37.5, -122),
      starttime = TimePointScala(0, "GMT"),
      totaltime = maxDelta,
      desttype = DestCategory("TEST")
    )

    val res = new GeneratorCoreScala().runLogic(req)

    assertTrue(res.routes.exists(_.steps.exists(_.isInstanceOf[FullRouteWalkStep])))
    assertTrue(res.routes.exists(_.steps.lengthCompare(1) == 0))
    assertTrue(res.routes.forall(_.steps.size % 2 == 1))
  }
}
