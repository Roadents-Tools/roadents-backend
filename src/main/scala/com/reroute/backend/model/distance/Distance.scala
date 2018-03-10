package com.reroute.backend.model.distance

import com.reroute.backend.model.time.TimeDelta

case class Distance(distance: Double) extends AnyVal with Ordered[Distance] {

  @inline
  def in(units: DistUnits): Double = distance / units.toMeters

  @inline
  def +(other: Distance) = Distance(this.distance + other.distance)

  @inline
  def -(other: Distance) = Distance(this.distance - other.distance)

  @inline
  def *(scalar: Double) = Distance(this.distance * scalar)

  @inline
  def /(scalar: Double) = Distance(this.distance / scalar)

  def aboutEquals(other: Distance): Boolean = {
    (this.distance - other.distance).abs <= Distance.ERROR_MARGIN.distance
  }

  def aboutEquals(other: Distance, margin: Distance): Boolean = {
    (this.distance - other.distance).abs <= margin.distance
  }

  @inline
  def avgWalkTime = TimeDelta((distance * 720).toLong)

  override def compare(that: Distance): Int = this.distance.compare(that.distance)
}

object Distance {
  final val NULL = Distance(0)
  final val ERROR_MARGIN = Distance(1)
  final val MAX_VALUE = Distance(Double.MaxValue)

  def apply(distance: Double, units: DistUnits): Distance = new Distance(distance * units.toMeters)

  implicit class DistanceMathOps(val n: Double) extends AnyVal {
    @inline
    def *(o: Distance): Distance = o * n
  }
}
