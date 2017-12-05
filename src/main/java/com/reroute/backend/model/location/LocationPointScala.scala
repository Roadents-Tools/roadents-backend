package com.reroute.backend.model.location

import com.reroute.backend.model.distance.{DistanceScala, DistanceUnitsScala}

trait LocationPointScala {

  val latitude: Double
  val longitude: Double

  def location: Array[Double] = Array(latitude, longitude)

  def overlaps(other: LocationPointScala): Boolean = {
    this.distanceTo(other) <= DistanceScala.ERROR_MARGIN
  }

  def distanceTo(other: LocationPointScala): DistanceScala = {

    val dLat = Math.toRadians(this.latitude - other.latitude)
    val dLng = Math.toRadians(this.longitude - other.longitude)

    val sindLat = Math.sin(dLat / 2)
    val sindLng = Math.sin(dLng / 2)

    val a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2) * Math.cos(Math.toRadians(other.latitude) * Math.cos(Math.toRadians(this.latitude)))

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val ckm = c * LocationPointScala.EARTH_RADIUS_KM
    DistanceScala(ckm, DistanceUnitsScala.KILOMETERS)
  }
}

object LocationPointScala {
  private final val EARTH_RADIUS_KM = 6367.449 //kilometers

  private final val AVG_WALKING_PER_HOUR = DistanceScala(5.0, DistanceUnitsScala.KILOMETERS)
  private final val MAX_TRANSIT_PER_HOUR = DistanceScala(65, DistanceUnitsScala.MILES)
  private final val LENGTH_DEGREE_LAT = DistanceScala(111, DistanceUnitsScala.KILOMETERS)
  private final val EQUATOR_LENGTH_DEGREE_LNG = DistanceScala(111.321, DistanceUnitsScala.KILOMETERS)
  private final val SAFETY_FACTOR = 1

  def latitudeRange(center: LocationPointScala, range: DistanceScala): Double = range.distance / LENGTH_DEGREE_LAT.distance

  def longitudeRange(center: LocationPointScala, range: DistanceScala): Double = {
    val unit = Math.cos(center.latitude) * EQUATOR_LENGTH_DEGREE_LNG.in(DistanceUnitsScala.KILOMETERS)
    range.in(DistanceUnitsScala.KILOMETERS) / unit
  }
}
