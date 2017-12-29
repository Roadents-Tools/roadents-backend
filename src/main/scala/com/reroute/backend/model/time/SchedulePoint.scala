package com.reroute.backend.model.time

import com.reroute.backend.model.database.{DatabaseID, DatabaseModel}

case class SchedulePoint(
                          packedTime: Int,
                          validDays: Byte,
                          fuzz: Int,
                          index: Int,
                          override val id: DatabaseID
                        ) extends DatabaseModel {

  def departsWithin(base: TimePoint, maxTime: TimeDelta): Boolean = {
    val endtime = base + maxTime
    val minday = base.dayOfWeek
    val maxday = endtime.dayOfWeek
    //TODO: Eventually make this not reliant on minday and maxday being next to each other
    if (!isValidDay(minday) || !isValidDay(maxday)) return false

    val deptime = packedTime + fuzz
    if (deptime % 86400 > endtime.packedTime) return false
    var comparor = base.withPackedTime(deptime % 86400)
    if (comparor < base) comparor += TimeDelta.DAY
    comparor <= endtime
  }

  def nextDeparture(base: TimePoint): TimePoint = {
    if (validDays == 0) return base + TimeDelta.WEEK * 52
    val generaldepart = packedTime + fuzz
    var rval = base.withPackedTime(generaldepart % 86400)
    if (rval.isBefore(base)) rval = rval + TimeDelta.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay + 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval.isBefore(base)) rval = rval + TimeDelta.WEEK
    rval

  }

  def nextValidTime(base: TimePoint): TimePoint = {
    if (validDays == 0) return base + TimeDelta.WEEK * 52
    if (isValidDay(base.dayOfWeek) && (base.packedTime >= this.packedTime && base.packedTime <= (this.packedTime + fuzz))) {
      return base
    }
    var rval = base.withPackedTime(this.packedTime % 86400)
    if (rval.isBefore(base)) rval = rval + TimeDelta.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay + 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval.isBefore(base)) rval = rval + TimeDelta.WEEK
    rval
  }

  def arrivesWithin(base: TimePoint, maxdelta: TimeDelta): Boolean = {
    val endtime = base + maxdelta
    val minday = base.dayOfWeek
    val maxday = endtime.dayOfWeek
    //TODO: Eventually make this not reliant on minday and maxday being next to each other
    if (!isValidDay(minday) || !isValidDay(maxday)) return false
    if (packedTime % 86400 > endtime.packedTime) return false
    var comparor = base.withPackedTime(packedTime % 86400)
    if (comparor < base) comparor += TimeDelta.DAY
    comparor <= endtime
  }

  @inline def isValidDay(day: Int): Boolean = (validDays & 1 << day) != 0
}
