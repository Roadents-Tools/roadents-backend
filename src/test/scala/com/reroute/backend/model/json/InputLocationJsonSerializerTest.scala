package com.reroute.backend.model.json

import com.reroute.backend.model.location.InputLocation
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class InputLocationJsonSerializerTest extends AssertionsForJUnit {

  @Test
  def testSeDese(): Unit = {
    val rng = new Random()
    val lat = 180 * (rng.nextDouble() - 0.5)
    val lng = 360 * (rng.nextDouble() - 0.5)
    val start = InputLocation(lat, lng)
    val serial = InputLocationJsonSerializer.serialize(start)
    val output = InputLocationJsonSerializer.deserialize(serial)
    assertEquals(s"Input != Output!\ninput: $start\nserial: $serial\noutput: $output", start, output)
  }
}
