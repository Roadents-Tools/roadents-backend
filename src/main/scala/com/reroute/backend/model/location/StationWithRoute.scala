package com.reroute.backend.model.location

import com.reroute.backend.model.time.{SchedulePoint, TimeDelta, TimePoint}

case class StationWithRoute(station: Station, route: TransitPath, schedule: Seq[SchedulePoint]) {

  def nextArrival(start: TimePoint): TimePoint = schedule.map(_.nextValidTime(start)).min

  def nextArrivalSched(start: TimePoint): SchedulePoint = schedule.minBy(_.nextValidTime(start))

  def nextDeparture(start: TimePoint): TimePoint = schedule.map(_.nextDeparture(start)).min

  def nextDepartureSched(start: TimePoint): SchedulePoint = schedule.minBy(_.nextDeparture(start))

  def departsWithin(start: TimePoint, maxdelta: TimeDelta): Boolean = {
    schedule.exists(_.departsWithin(start, maxdelta))
  }
}
