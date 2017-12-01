package com.reroute.backend.model.time

import java.util.{Calendar, TimeZone}


final class TimePointScala private(
                                    val unixtime: Long,
                                    val timezone: String,
                                    private val offset: Long,
                                    private var calendar: Option[Calendar] = None
                                  ) extends Ordered[TimePointScala] {

  def milliseconds: Int = (unixtime % TimePointScala.SECONDS_TO_MILLIS).asInstanceOf[Int]

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

  def withSecond(second: Int): TimePointScala = {
    if (second < 0 || second > 60) throw new IllegalArgumentException("Second invalid.")
    val secDiff = second - this.seconds
    val milliDiff = secDiff * TimePointScala.SECONDS_TO_MILLIS
    this + TimeDeltaScala(milliDiff)
  }

  def seconds: Int = ((unixtime / TimePointScala.SECONDS_TO_MILLIS) % 60).asInstanceOf[Int]

  def withMinute(minute: Int): TimePointScala = {
    if (minute < 0 || minute > 60) throw new IllegalArgumentException("Minute invalid.")
    val minDiff = minute - this.minutes
    val milliDiff = minDiff * TimePointScala.MINUTES_TO_MILLIS
    this + TimeDeltaScala(milliDiff)
  }

  def minutes: Int = ((unixtime / TimePointScala.MINUTES_TO_MILLIS) % 60).asInstanceOf[Int]

  def withHour(hour: Int): TimePointScala = {
    if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour invalid.")
    val hourDiff = hour - this.hours
    val milliDiff = hourDiff * TimePointScala.HOURS_TO_MILLIS
    this + TimeDeltaScala(milliDiff)
  }

  def hours: Int = (((unixtime + offset) / TimePointScala.HOURS_TO_MILLIS) % 24).asInstanceOf[Int]

  def +(delta: TimeDeltaScala): TimePointScala = TimePointScala(this.unixtime + delta.unixdelta, this.timezone, this.offset, this.calendar)

  def withPackedTime(time: Int): TimePointScala = {
    if (packedTime < 0 || packedTime >= 86400) throw new IllegalArgumentException("Packed time invalid.")
    val seconddiff = packedTime - this.packedTime
    val millidiff = seconddiff * TimePointScala.SECONDS_TO_MILLIS
    this + TimeDeltaScala(millidiff)
  }

  def packedTime: Int = (((this.unixtime + offset) % TimePointScala.DAYS_TO_MILLIS) / 1000).asInstanceOf[Int]

  def withDayOfWeek(dayOfWeek: Int): TimePointScala = {
    if (dayOfWeek < 0 || dayOfWeek > 7) throw new IllegalArgumentException("Day of week invalid.")
    val dayDiff = dayOfWeek - this.dayOfWeek
    val milliDiff = dayDiff * TimePointScala.DAYS_TO_MILLIS
    this + TimeDeltaScala(milliDiff)
  }

  def dayOfWeek: Int = ((((unixtime + offset) / TimePointScala.DAYS_TO_MILLIS) + 3) % 7).asInstanceOf[Int]

  def withDayOfMonth(dayOfMonth: Int): TimePointScala = {
    if (dayOfMonth < 0 || dayOfMonth > 31) throw new IllegalArgumentException("Day of month invalid.")
    val dayDiff = dayOfMonth - this.dayOfMonth
    val milliDiff = dayDiff * TimePointScala.DAYS_TO_MILLIS
    this + TimeDeltaScala(milliDiff)
  }

  def dayOfMonth: Int = getCalendar.get(Calendar.DAY_OF_MONTH)

  def -(delta: TimeDeltaScala): TimePointScala = TimePointScala(this.unixtime - delta.unixdelta, this.timezone, this.offset, this.calendar)

  def timeUntil(other: TimePointScala): TimeDeltaScala = TimeDeltaScala(other.unixtime - this.unixtime)

  def isBefore(other: TimePointScala): Boolean = other.unixtime > this.unixtime

  override def equals(other: Any): Boolean = other match {
    case that: TimePointScala => unixtime == that.unixtime && timezone == that.timezone
    case _ => false
  }

  override def hashCode(): Int = unixtime.hashCode() * 31 + timezone.hashCode

  override def toString = s"TimePointScala($unixtime, $timezone)"

  override def compare(that: TimePointScala): Int = this.unixtime.compare(that.unixtime)

  private def this(unixtime: Long, timezone: String) = this(unixtime, timezone, TimeZone.getTimeZone(timezone).getOffset(unixtime), None)
}

object TimePointScala {

  final val SECONDS_TO_MILLIS = 1000
  final val MINUTES_TO_MILLIS = 60 * SECONDS_TO_MILLIS
  final val HOURS_TO_MILLIS = 60 * MINUTES_TO_MILLIS
  final val DAYS_TO_MILLIS = 24 * HOURS_TO_MILLIS
  final val WEEKS_TO_MILLIS = 7 * DAYS_TO_MILLIS
  final val MONTHES_TO_MILLIS = 31 * DAYS_TO_MILLIS
  final val YEARS_TO_MILLIS = 365 * DAYS_TO_MILLIS + DAYS_TO_MILLIS / 4
  val NULL = TimePointScala(0, "GMT")

  def now(): TimePointScala = now("GMT")

  def now(timezone: String): TimePointScala = new TimePointScala(System.currentTimeMillis(), timezone)

  def apply(unixtime: Long, timezone: String) = new TimePointScala(unixtime, timezone)

  private def apply(unixtime: Long, timezone: String, offset: Long, calendar: Option[Calendar]) = {
    new TimePointScala(unixtime, timezone, offset, calendar)
  }
}
