package com.reroute.backend.model.distance

import com.reroute.backend.model.time.TimeDeltaScala

case class DistanceScala(distance: Double) extends Ordered[DistanceScala] {

  def in(units: DistanceUnitsScala): Double = distance / units.toMeters

  def +(other: DistanceScala) = DistanceScala(this.distance + other.distance)

  def -(other: DistanceScala) = DistanceScala(this.distance - other.distance)

  def *(scalar: Double) = DistanceScala(this.distance * scalar)

  def /(scalar: Double) = DistanceScala(this.distance / scalar)

  def aboutEquals(other: DistanceScala): Boolean = {
    (this.distance - other.distance).abs <= DistanceScala.ERROR_MARGIN.distance
  }

  def aboutEquals(other: DistanceScala, margin: DistanceScala): Boolean = {
    (this.distance - other.distance).abs <= margin.distance
  }

  def avgWalkTime = TimeDeltaScala((distance * 720).toLong)

  override def compare(that: DistanceScala): Int = this.distance.compare(that.distance)
}

object DistanceScala {
  val NULL = DistanceScala(0)
  val ERROR_MARGIN = DistanceScala(.1)

  def apply(distance: Double, units: DistanceUnitsScala): DistanceScala = new DistanceScala(distance * units.toMeters)
}
