package com.reroute.backend.model.json

import com.reroute.backend.model.location.{DestCategory, DestinationScala}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class DestinationJsonSerializerTest extends AssertionsForJUnit {

  @Test
  def testSeDese(): Unit = {
    val rng = new Random()
    val lat = 180 * (rng.nextDouble() - 0.5)
    val lng = 360 * (rng.nextDouble() - 0.5)
    val start = DestinationScala("TEST", lat, lng, rng.alphanumeric.grouped(10).take(10).map(chars => chars.mkString).map(DestCategory(_)).toSeq)
    val serial = DestinationJsonSerializer.serialize(start)
    val output = DestinationJsonSerializer.deserialize(serial)
    assertEquals(s"Input != Output!\ninput: $start\nserial: $serial\noutput: $output", start, output)
  }
}
