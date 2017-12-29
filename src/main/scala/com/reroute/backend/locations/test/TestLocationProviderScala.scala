package com.reroute.backend.locations.test

import java.util.Random

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProviderScala
import com.reroute.backend.model.location._

import scala.collection.breakOut

/**
 * A location provider for use in tests.
 */
object TestLocationProviderScala {
  private final val MULTIPLIERS = Array[(Double, Double)](
    (.99, 0),
    (-.99, 0),
    (0, .99),
    (0, -.99)
  )
  private final val rng = new Random

  private def buildNullLocations(req: LocationsRequest) = MULTIPLIERS.map(mult => {
    val center = req.center
    val dist = req.timeRange.avgWalkDist
    val newLat = center.latitude + LocationPointScala.latitudeRange(center, dist) * mult._1
    val newLong = center.longitude + LocationPointScala.longitudeRange(center, dist) * mult._2
    val name = s"${req.searchquery.category} Place ${rng.nextInt(100)}"
    DestinationScala(name, newLat, newLong, Seq(req.searchquery))
  })(breakOut)
}

class TestLocationProviderScala extends LocationProviderScala {
  override def queryLocations(requests: Seq[LocationsRequest]): Map[LocationsRequest, Seq[DestinationScala]] = {
    requests
      .map(req => req -> TestLocationProviderScala.buildNullLocations(req))(breakOut)
  }

  override def validityFilter(req: LocationsRequest): Boolean = true

  override def isUp(): Boolean = true

  override def close(): Unit = {}
}