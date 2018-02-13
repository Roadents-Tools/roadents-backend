package com.reroute.backend.model.time

import com.reroute.backend.model.distance.{DistUnits, Distance}

case class TimeDelta(unixdelta: Long) extends AnyVal with Ordered[TimeDelta] {

  @inline
  def +(other: TimeDelta): TimeDelta = TimeDelta(unixdelta + other.unixdelta)

  @inline
  def -(other: TimeDelta): TimeDelta = TimeDelta(unixdelta - other.unixdelta)

  @inline
  def *(scaler: Double): TimeDelta = TimeDelta((unixdelta * scaler).asInstanceOf[Long])

  @inline
  def /(scaler: Double): TimeDelta = TimeDelta((unixdelta / scaler).asInstanceOf[Long])

  @inline
  def seconds: Double = unixdelta / 1000.0

  @inline
  def hours: Double = unixdelta / (60 * (60 * 1000.0))

  @inline
  def days: Double = unixdelta / (24 * (60 * (60 * 1000.0)))

  @inline
  def avgWalkDist: Distance = TimeDelta.AVG_WALK_SPEED_PER_MINUTE * this.minutes.abs

  @inline
  def minutes: Double = unixdelta / (60 * 1000.0)

  @inline
  def abs: TimeDelta = TimeDelta(math.abs(unixdelta))

  override def compare(that: TimeDelta): Int = unixdelta.compareTo(that.unixdelta)
}

object TimeDelta {
  final val NULL = TimeDelta(0)
  final val SECOND = TimeDelta(1000)
  final val MINUTE = TimeDelta(60000L)
  final val HOUR = TimeDelta(3600000L)
  final val DAY = TimeDelta(86400000L)
  final val WEEK = TimeDelta(604800000L)
  final val AVG_WALK_SPEED_PER_MINUTE = Distance(5000.0 / 60, DistUnits.METERS)
  final val MAX_SPEED_PER_MINUTE = Distance(100.0 / 60, DistUnits.KILOMETERS)
  final val MAX_VALUE = TimeDelta(Long.MaxValue)

  implicit class DecimalTimeDeltaMathWrapper(val n: Double) extends AnyVal {
    def *(dt: TimeDelta): TimeDelta = dt * n
  }
}