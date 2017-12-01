package com.reroute.backend.model.time

import com.reroute.backend.model.database.{DatabaseIDScala, DatabaseObjectScala}

case class SchedulePointScala(
                               packedTime: Int,
                               validDays: Byte,
                               fuzz: Int,
                               override val id: Option[DatabaseIDScala] = None
                             ) extends DatabaseObjectScala {

  def nextValidTime(base: TimePointScala): TimePointScala = {
    if (validDays == 0) return TimePointScala.NULL
    if (isValidDay(base.dayOfWeek) && Math.abs(base.packedTime - this.packedTime) <= fuzz) return base
    var rval = base.withPackedTime(this.packedTime)
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

  @inline def isValidDay(day: Int): Boolean = (validDays & 1 << day) != 0
}
