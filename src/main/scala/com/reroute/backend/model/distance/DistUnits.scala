package com.reroute.backend.model.distance

case class DistUnits private(toMeters: Double) extends AnyVal

object DistUnits {
  final val METERS = DistUnits(1)
  final val KILOMETERS = DistUnits(1000)
  final val MILES = DistUnits(1609.344)
  final val FEET = DistUnits(.3048)
  final val AVG_WALK_MINUTES = DistUnits(5000.0 / 60.0)

}
