package com.reroute.backend.model.location

import com.reroute.backend.model.distance.{DistanceScala, DistanceUnitsScala}

trait LocationPointScala {

  private final val EARTH_RADIUS_KM = 6367.449; //kilometers
  val latitude: Double
  val longitude: Double

  def location: Array[Double] = Array(latitude, longitude)

  def overlaps(other: LocationPointScala): Boolean = {
    this.distanceTo(other).distance <= DistanceScala.ERROR_MARGIN.distance
  }

  def distanceTo(other: LocationPointScala): DistanceScala = {

    val dLat = Math.toRadians(this.latitude - other.latitude)
    val dLng = Math.toRadians(this.longitude - other.longitude)

    val sindLat = Math.sin(dLat / 2)
    val sindLng = Math.sin(dLng / 2)

    val a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2) * Math.cos(Math.toRadians(other.latitude) * Math.cos(Math.toRadians(this.latitude)))

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val ckm = c * EARTH_RADIUS_KM
    DistanceScala(ckm, DistanceUnitsScala.KILOMETERS)
  }

}
