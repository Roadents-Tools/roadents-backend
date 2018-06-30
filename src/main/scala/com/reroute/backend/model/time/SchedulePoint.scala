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
    if (maxTime < TimeDelta.NULL) {
      val prevDep = prevDeparture(base)
      base + maxTime < prevDep && prevDep < base
    }
    else {
      val nDep = nextDeparture(base)
      base < nDep && nDep < base + maxTime
    }
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

  def prevArrival(base: TimePoint): TimePoint = {
    if (validDays == 0) return base - TimeDelta.WEEK * 52
    var rval = base.withPackedTime(packedTime % 86400)
    if (rval > base) rval = rval - TimeDelta.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay - 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval > base) rval = rval - TimeDelta.WEEK
    rval
  }

  def prevDeparture(base: TimePoint): TimePoint = {
    if (validDays == 0) return base - TimeDelta.WEEK * 52
    if (isValidDay(base.dayOfWeek) && (base.packedTime >= this.packedTime && base.packedTime <= (this.packedTime + fuzz))) {
      return base
    }
    var rval = base.withPackedTime((packedTime + fuzz) % 86400)
    if (rval > base) rval = rval - TimeDelta.DAY
    var workingDay = rval.dayOfWeek
    while (!isValidDay(workingDay)) {
      workingDay = (workingDay - 1) % 7
    }
    if (workingDay != rval.dayOfWeek) {
      rval = rval.withDayOfWeek(workingDay)
    }
    if (rval > base) rval = rval - TimeDelta.WEEK
    assert(rval < base)
    rval
  }

  def nextArrival(base: TimePoint): TimePoint = {
    if (validDays == 0) return base + TimeDelta.WEEK * 52
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
    val baseDay = base.dayOfWeek
    val leftDay = baseDay + maxdelta.days.toInt

    val minDay = math.min(baseDay, leftDay)
    val maxDay = math.max(baseDay, leftDay)
    val daysWork = (minDay to maxDay).map(_ % 7).exists(isValidDay)
    if (!daysWork) return false

    val leftTime = base + maxdelta
    val minTime = Seq(leftTime, base).min
    val nextArrival = minTime.withPackedTime(packedTime % 86400)

    (base > nextArrival && nextArrival > leftTime) || (base < nextArrival && nextArrival < leftTime)
  }

  @inline def isValidDay(day: Int): Boolean = (validDays & 1 << day) != 0
}
