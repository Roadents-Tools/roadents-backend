package com.reroute.backend.model.time

case class TimeDeltaScala(unixdelta: Long) extends Ordered[TimeDeltaScala] {

  def +(other: TimeDeltaScala): TimeDeltaScala = TimeDeltaScala(unixdelta + other.unixdelta)

  def -(other: TimeDeltaScala): TimeDeltaScala = TimeDeltaScala(unixdelta - other.unixdelta)

  def *(scaler: Double): TimeDeltaScala = TimeDeltaScala((unixdelta * scaler).asInstanceOf[Long])

  def /(scaler: Double): TimeDeltaScala = TimeDeltaScala((unixdelta / scaler).asInstanceOf[Long])

  def seconds: Double = unixdelta / 1000.0

  def minutes: Double = unixdelta / (60 * 1000.0)

  def hours: Double = unixdelta / (60 * (60 * 1000.0))

  def days: Double = unixdelta / (24 * (60 * (60 * 1000.0)))

  override def compare(that: TimeDeltaScala): Int = unixdelta.compareTo(that.unixdelta)
}

object TimeDeltaScala {
  final val NULL = TimeDeltaScala(0)
  final val SECOND = TimeDeltaScala(1000)
  final val MINUTE = TimeDeltaScala(60000L)
  final val HOUR = TimeDeltaScala(3600000L)
  final val DAY = TimeDeltaScala(86400000L)
  final val WEEK = TimeDeltaScala(604800000L)
}