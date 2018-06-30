package com.reroute.backend.model.location

import com.reroute.backend.model.distance.Distance
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class LocationPointTest extends AssertionsForJUnit {

  @Test
  def testDistanceSymmetry(): Unit = {
    val rng = new Random()
    val locs = (0 to 500).toStream.map(
      _ => InputLocation(180 * (rng.nextDouble() - .5), 360 * (rng.nextDouble() - 0.5)))

    for (l1 <- locs; l2 <- locs) {
      val diff = (l1 distanceTo l2) - (l2 distanceTo l1)
      assertTrue(s"Bad locs at $l1, $l2. Diff of $diff. Went from ${l1 distanceTo l2} to ${l2 distanceTo l1}", diff < Distance.ERROR_MARGIN)
    }
  }
}
