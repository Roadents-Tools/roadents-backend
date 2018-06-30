package com.reroute.backend.model.location

import com.reroute.backend.model.distance.{DistUnits, Distance}

trait LocationPoint {

  val latitude: Double
  val longitude: Double

  def location: Array[Double] = Array(latitude, longitude)

  def overlaps(other: LocationPoint): Boolean = {
    Math.abs(this.latitude - other.latitude) <= 0.00001 && Math.abs(this.longitude - other.longitude) <= 0.00001
  }

  def distanceTo(other: LocationPoint): Distance = {
    val dlat = (latitude - other.latitude).abs
    val dlng = (longitude - other.longitude).abs
    if (dlat <= 0.01 && dlng <= 0.01) rectDist(other)
    else haversineDist(other)
  }

  @inline
  private def rectDist(other: LocationPoint): Distance = {
    val deglen = LocationPoint.LENGTH_DEGREE_LAT
    val dx = other.latitude - this.latitude
    val dy = (other.longitude - this.longitude) * Math.cos(Math.toRadians(this.latitude))
    val coeff = math.sqrt(dx * dx + dy * dy)
    deglen * coeff
  }

  @inline
  private def haversineDist(other: LocationPoint): Distance = {

    val dLat = Math.toRadians(this.latitude - other.latitude)
    val dLng = Math.toRadians(this.longitude - other.longitude)

    val sindLat = Math.pow(Math.sin(dLat / 2), 2)
    val sindLng = Math.pow(Math.sin(dLng / 2), 2)

    val a = sindLat + sindLng * Math.cos(Math.toRadians(other.latitude)) * Math.cos(Math.toRadians(this.latitude))

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val ckm = c * LocationPoint.EARTH_RADIUS_KM
    Distance(ckm, DistUnits.KILOMETERS)

  }
}

object LocationPoint {
  final val EARTH_RADIUS_KM = 6367.449 //kilometers

  final val AVG_WALKING_PER_HOUR = Distance(5.0, DistUnits.KILOMETERS)
  final val MAX_TRANSIT_PER_HOUR = Distance(65, DistUnits.MILES)
  final val LENGTH_DEGREE_LAT = Distance(111, DistUnits.KILOMETERS)
  final val EQUATOR_LENGTH_DEGREE_LNG = Distance(111.321, DistUnits.KILOMETERS)
  final val SAFETY_FACTOR = 1

  @inline
  def latitudeRange(center: LocationPoint, range: Distance): Double = range.distance / LENGTH_DEGREE_LAT.distance

  @inline
  def longitudeRange(center: LocationPoint, range: Distance): Double = {
    range.distance / (Math.cos(center.latitude) * EQUATOR_LENGTH_DEGREE_LNG.distance)
  }

}
