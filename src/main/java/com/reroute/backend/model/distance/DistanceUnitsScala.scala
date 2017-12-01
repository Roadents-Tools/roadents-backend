package com.reroute.backend.model.distance

case class DistanceUnitsScala(toMeters: Double) extends AnyVal

object DistanceUnitsScala {
  val METERS = DistanceUnitsScala(1)
  val KILOMETERS = DistanceUnitsScala(1000)
  val MILES = DistanceUnitsScala(1609.344)
  val FEET = DistanceUnitsScala(.3048)
}
