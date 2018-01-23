package com.reroute.backend.logic.donut

import com.reroute.backend.locations.helpers.LocationProviderManager
import com.reroute.backend.model.location.{DestCategory, InputLocation}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.helpers.StationDatabaseManager
import org.junit.Assert._
import org.junit.{After, Before, Test}
import org.scalatest.junit.AssertionsForJUnit

class DonutCoreTest extends AssertionsForJUnit {

  @Before
  def init(): Unit = {
    StationDatabaseManager.setTest(true)
    LocationProviderManager.setTest(true)
  }

  @Test
  def testDonut(): Unit = {
    val maxDelta = TimeDelta(120 * 60 * 1000)
    val req = DonutRequest(
      startPoint = InputLocation(37.5, -122),
      inpstarttime = Some(TimePoint(0, "GMT")),
      maxDelta = maxDelta,
      inmaxwalktime = Some(15 * TimeDelta.MINUTE),
      desttype = DestCategory("TEST")
    )

    val res = DonutCore.runLogic(req)

    assertTrue(s"Got errors: ${res.errors}", res.errors.isEmpty)
    assertTrue(res.routes.exists(_.steps.lengthCompare(1) == 0))
    assertTrue(res.routes.forall(_.steps.size % 2 == 1))
    assertTrue(res.routes.lengthCompare(5) >= 0)
  }

  @After
  def teardown(): Unit = {
    StationDatabaseManager.setTest(false)
    LocationProviderManager.setTest(false)
  }
}
