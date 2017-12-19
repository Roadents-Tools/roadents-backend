package com.reroute.backend.model.json

import com.reroute.backend.model.location.StartScala
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class StartJsonSerializerTest extends AssertionsForJUnit {

  @Test
  def testSeDese(): Unit = {
    val rng = new Random()
    val lat = 180 * (rng.nextDouble() - 0.5)
    val lng = 360 * (rng.nextDouble() - 0.5)
    val start = StartScala(lat, lng)
    val serial = StartJsonSerializer.serialize(start)
    val output = StartJsonSerializer.deserialize(serial)
    assertEquals(s"Input != Output!\ninput: $start\nserial: $serial\noutput: $output", start, output)
  }
}
