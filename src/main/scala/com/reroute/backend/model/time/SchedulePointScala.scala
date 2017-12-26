package com.reroute.backend.model.time

import com.reroute.backend.model.database.{DatabaseIDScala, DatabaseObjectScala}

case class SchedulePointScala(
                               packedTime: Int,
                               validDays: Byte,
                               fuzz: Int,
                               index: Int,
                               override val id: DatabaseIDScala
                             ) extends DatabaseObjectScala {

  def departsWithin(base: TimePointScala, maxTime: TimeDeltaScala): Boolean = {
    val endtime = base + maxTime
    val minday = base.dayOfWeek
    val maxday = endtime.dayOfWeek
    //TODO: Eventually make this not reliant on minday and maxday being next to each other
    if (!isValidDay(minday) || !isValidDay(maxday)) return false

    val deptime = packedTime + fuzz
    if (deptime % 86400 > endtime.packedTime) return false
    var comparor = base.withPackedTime(deptime % 86400)
    if (comparor < base) comparor += TimeDeltaScala.DAY
    comparor <= endtime
  }

  def nextDeparture(base: TimePointScala): TimePointScala = {
    val generaldepart = packedTime + fuzz
    var rval = base.withPackedTime(generaldepart % 86400)
    if (rval.isBefore(base)) rval = rval + TimeDeltaScala.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay + 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval.isBefore(base)) rval = rval + TimeDeltaScala.WEEK
    rval

  }

  def nextValidTime(base: TimePointScala): TimePointScala = {
    if (validDays == 0) return base + TimeDeltaScala.WEEK * 52
    if (isValidDay(base.dayOfWeek) && (base.packedTime >= this.packedTime && base.packedTime <= (this.packedTime + fuzz))) {
      println(s"${base.packedTime} marked between ${this.packedTime}, ${this.packedTime + fuzz}")
      return base
    }
    var rval = base.withPackedTime(this.packedTime % 86400)
    if (rval.isBefore(base)) rval = rval + TimeDeltaScala.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay + 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval.isBefore(base)) rval = rval + TimeDeltaScala.WEEK
    rval
  }

  def arrivesWithin(base: TimePointScala, maxdelta: TimeDeltaScala): Boolean = {
    val endtime = base + maxdelta
    val minday = base.dayOfWeek
    val maxday = endtime.dayOfWeek
    //TODO: Eventually make this not reliant on minday and maxday being next to each other
    if (!isValidDay(minday) || !isValidDay(maxday)) return false
    if (packedTime % 86400 > endtime.packedTime) return false
    var comparor = base.withPackedTime(packedTime % 86400)
    if (comparor < base) comparor += TimeDeltaScala.DAY
    comparor <= endtime
  }

  @inline def isValidDay(day: Int): Boolean = (validDays & 1 << day) != 0
}
