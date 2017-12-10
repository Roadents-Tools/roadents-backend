package com.reroute.backend.model.distance

case class DistanceUnitsScala(toMeters: Double) extends AnyVal

object DistanceUnitsScala {
  final val METERS = DistanceUnitsScala(1)
  final val KILOMETERS = DistanceUnitsScala(1000)
  final val MILES = DistanceUnitsScala(1609.344)
  final val FEET = DistanceUnitsScala(.3048)
  final val AVG_WALK_MINUTES = DistanceUnitsScala(60.0 / 5000)
}
