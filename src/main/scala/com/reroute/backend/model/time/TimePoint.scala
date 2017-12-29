package com.reroute.backend.model.time

import java.util.{Calendar, TimeZone}


final class TimePoint private(
                               val unixtime: Long,
                               val timezone: String,
                               private val offset: Long,
                               private var calendar: Option[Calendar] = None
                             ) extends Ordered[TimePoint] {

  def milliseconds: Int = (unixtime % TimePoint.SECONDS_TO_MILLIS).asInstanceOf[Int]

  def month: Int = getCalendar.get(Calendar.MONTH) + 1

  def year: Int = getCalendar.get(Calendar.YEAR)

  private def getCalendar: Calendar = calendar match {
    case Some(cal) => cal
    case None =>
      val ncal: Calendar = Calendar.getInstance(TimeZone.getTimeZone(timezone))
      ncal.setTimeInMillis(unixtime)
      this.calendar = Some(ncal)
      ncal
  }

  def withSecond(second: Int): TimePoint = {
    if (second < 0 || second > 60) throw new IllegalArgumentException("Second invalid.")
    val secDiff = if (second >= this.seconds) second - this.seconds else 60 + second - this.seconds
    val milliDiff = secDiff * TimePoint.SECONDS_TO_MILLIS
    this + TimeDelta(milliDiff)
  }

  def seconds: Int = ((unixtime / TimePoint.SECONDS_TO_MILLIS) % 60).asInstanceOf[Int]

  def withMinute(minute: Int): TimePoint = {
    if (minute < 0 || minute > 60) throw new IllegalArgumentException("Minute invalid.")
    val minDiff = if (minute >= this.minutes) minute - this.minutes else 60 + minute - this.minutes
    val milliDiff = minDiff * TimePoint.MINUTES_TO_MILLIS
    this + TimeDelta(milliDiff)
  }

  def minutes: Int = ((unixtime / TimePoint.MINUTES_TO_MILLIS) % 60).asInstanceOf[Int]

  def withHour(hour: Int): TimePoint = {
    if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour invalid.")
    val hourDiff = if (hour >= this.hours) hour - this.hours else 24 + hour - this.hours
    val milliDiff = hourDiff * TimePoint.HOURS_TO_MILLIS
    this + TimeDelta(milliDiff)
  }

  def hours: Int = (((unixtime + offset) / TimePoint.HOURS_TO_MILLIS) % 24).asInstanceOf[Int]

  def +(delta: TimeDelta): TimePoint = TimePoint(this.unixtime + delta.unixdelta, this.timezone, this.offset, this.calendar)

  def withPackedTime(packedTime: Int): TimePoint = {
    if (packedTime < 0 || packedTime >= 86400) throw new IllegalArgumentException(s"Packed $packedTime time invalid.")
    val seconddiff = if (packedTime >= this.packedTime) packedTime - this.packedTime else 86400 + packedTime - this.packedTime
    val millidiff = seconddiff * TimePoint.SECONDS_TO_MILLIS
    this + TimeDelta(millidiff)
  }

  def packedTime: Int = (((this.unixtime + offset) % TimePoint.DAYS_TO_MILLIS) / 1000).asInstanceOf[Int]

  def withDayOfWeek(dayOfWeek: Int): TimePoint = {
    if (dayOfWeek < 0 || dayOfWeek > 7) throw new IllegalArgumentException("Day of week invalid.")
    val dayDiff = if (dayOfWeek >= this.dayOfWeek) dayOfWeek - this.dayOfWeek else 7 + dayOfWeek - this.dayOfWeek
    val milliDiff = dayDiff * TimePoint.DAYS_TO_MILLIS
    this + TimeDelta(milliDiff)
  }

  def dayOfWeek: Int = ((((unixtime + offset) / TimePoint.DAYS_TO_MILLIS) + 3) % 7).asInstanceOf[Int]

  def withDayOfMonth(dayOfMonth: Int): TimePoint = {
    if (dayOfMonth < 0 || dayOfMonth > 31) throw new IllegalArgumentException("Day of month invalid.")
    val dayDiff = dayOfMonth - this.dayOfMonth
    val milliDiff = dayDiff * TimePoint.DAYS_TO_MILLIS
    this + TimeDelta(milliDiff)
  }

  def dayOfMonth: Int = getCalendar.get(Calendar.DAY_OF_MONTH)

  def -(delta: TimeDelta): TimePoint = TimePoint(this.unixtime - delta.unixdelta, this.timezone, this.offset, this.calendar)

  def timeUntil(other: TimePoint): TimeDelta = TimeDelta(other.unixtime - this.unixtime)

  def isBefore(other: TimePoint): Boolean = other.unixtime > this.unixtime

  override def equals(other: Any): Boolean = other match {
    case that: TimePoint => unixtime == that.unixtime && timezone == that.timezone
    case _ => false
  }

  override def hashCode(): Int = unixtime.hashCode() * 31 + timezone.hashCode

  override def toString = s"TimePoint($unixtime, $timezone)"

  override def compare(that: TimePoint): Int = this.unixtime.compare(that.unixtime)

  private def this(unixtime: Long,
                   timezone: String) = this(unixtime, timezone, TimeZone.getTimeZone(timezone).getOffset(unixtime), None)
}

object TimePoint {

  final val SECONDS_TO_MILLIS = 1000
  final val MINUTES_TO_MILLIS = 60 * SECONDS_TO_MILLIS
  final val HOURS_TO_MILLIS = 60 * MINUTES_TO_MILLIS
  final val DAYS_TO_MILLIS = 24 * HOURS_TO_MILLIS
  final val WEEKS_TO_MILLIS = 7 * DAYS_TO_MILLIS
  final val MONTHES_TO_MILLIS = 31 * DAYS_TO_MILLIS
  final val YEARS_TO_MILLIS = 365 * DAYS_TO_MILLIS + DAYS_TO_MILLIS / 4
  val NULL = TimePoint(0, "GMT")

  def now(): TimePoint = now("GMT")

  def now(timezone: String): TimePoint = new TimePoint(System.currentTimeMillis(), timezone)

  def apply(unixtime: Long, timezone: String) = new TimePoint(unixtime, timezone)

  private def apply(unixtime: Long, timezone: String, offset: Long, calendar: Option[Calendar]) = {
    new TimePoint(unixtime, timezone, offset, calendar)
  }
}
