package com.reroute.backend.model.time

import com.reroute.backend.model.distance.{DistanceScala, DistanceUnitsScala}

case class TimeDeltaScala(unixdelta: Long) extends AnyVal with Ordered[TimeDeltaScala] {

  @inline
  def +(other: TimeDeltaScala): TimeDeltaScala = TimeDeltaScala(unixdelta + other.unixdelta)

  @inline
  def -(other: TimeDeltaScala): TimeDeltaScala = TimeDeltaScala(unixdelta - other.unixdelta)

  @inline
  def *(scaler: Double): TimeDeltaScala = TimeDeltaScala((unixdelta * scaler).asInstanceOf[Long])

  @inline
  def /(scaler: Double): TimeDeltaScala = TimeDeltaScala((unixdelta / scaler).asInstanceOf[Long])

  @inline
  def seconds: Double = unixdelta / 1000.0

  @inline
  def hours: Double = unixdelta / (60 * (60 * 1000.0))

  @inline
  def days: Double = unixdelta / (24 * (60 * (60 * 1000.0)))

  @inline
  def avgWalkDist: DistanceScala = TimeDeltaScala.AVG_WALK_SPEED_PER_MINUTE * this.minutes

  @inline
  def minutes: Double = unixdelta / (60 * 1000.0)

  override def compare(that: TimeDeltaScala): Int = unixdelta.compareTo(that.unixdelta)
}

object TimeDeltaScala {
  final val NULL = TimeDeltaScala(0)
  final val SECOND = TimeDeltaScala(1000)
  final val MINUTE = TimeDeltaScala(60000L)
  final val HOUR = TimeDeltaScala(3600000L)
  final val DAY = TimeDeltaScala(86400000L)
  final val WEEK = TimeDeltaScala(604800000L)
  final val AVG_WALK_SPEED_PER_MINUTE = DistanceScala(5000.0 / 60, DistanceUnitsScala.METERS)
  final val MAX_SPEED_PER_MINUTE = DistanceScala(100.0 / 60, DistanceUnitsScala.KILOMETERS)
  final val MAX_VALUE = TimeDeltaScala(Long.MaxValue)

  implicit class DecimalTimeDeltaMathWrapper(val n: Double) extends AnyVal {
    def *(dt: TimeDeltaScala) = dt * n
  }
}