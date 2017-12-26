package com.reroute.backend.model.location

import com.reroute.backend.model.time.{SchedulePointScala, TimeDeltaScala, TimePointScala}

case class StationWithRoute(station: StationScala, route: TransitPathScala, schedule: Seq[SchedulePointScala]) {

  def nextArrival(start: TimePointScala): TimePointScala = schedule.map(_.nextValidTime(start)).min

  def nextArrivalSched(start: TimePointScala): SchedulePointScala = schedule.minBy(_.nextValidTime(start))

  def nextDeparture(start: TimePointScala): TimePointScala = schedule.map(_.nextDeparture(start)).min

  def nextDepartureSched(start: TimePointScala): SchedulePointScala = schedule.minBy(_.nextDeparture(start))

  def departsWithin(start: TimePointScala, maxdelta: TimeDeltaScala): Boolean = {
    schedule.exists(_.departsWithin(start, maxdelta))
  }
}
