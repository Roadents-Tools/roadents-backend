package com.reroute.backend.model.distance

import com.reroute.backend.model.time.TimeDeltaScala

case class DistanceScala(distance: Double) extends AnyVal with Ordered[DistanceScala] {

  @inline
  def in(units: DistanceUnitsScala): Double = distance / units.toMeters

  @inline
  def +(other: DistanceScala) = DistanceScala(this.distance + other.distance)

  @inline
  def -(other: DistanceScala) = DistanceScala(this.distance - other.distance)

  @inline
  def *(scalar: Double) = DistanceScala(this.distance * scalar)

  @inline
  def /(scalar: Double) = DistanceScala(this.distance / scalar)

  def aboutEquals(other: DistanceScala): Boolean = {
    (this.distance - other.distance).abs <= DistanceScala.ERROR_MARGIN.distance
  }

  def aboutEquals(other: DistanceScala, margin: DistanceScala): Boolean = {
    (this.distance - other.distance).abs <= margin.distance
  }

  @inline
  def avgWalkTime = TimeDeltaScala((distance * 720).toLong)

  override def compare(that: DistanceScala): Int = this.distance.compare(that.distance)
}

object DistanceScala {
  final val NULL = DistanceScala(0)
  final val ERROR_MARGIN = DistanceScala(1)
  final val MAX_VALUE = DistanceScala(Double.MaxValue)

  def apply(distance: Double, units: DistanceUnitsScala): DistanceScala = new DistanceScala(distance * units.toMeters)
}
