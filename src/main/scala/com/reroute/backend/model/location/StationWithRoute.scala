package com.reroute.backend.model.location

import com.reroute.backend.model.time.{SchedulePointScala, TimePointScala}

case class StationWithRoute(station: StationScala, route: TransitPathScala, schedule: Seq[SchedulePointScala]) {

  def nextArrival(start: TimePointScala): TimePointScala = schedule.map(_.nextValidTime(start)).min

  def nextArrivalSched(start: TimePointScala): SchedulePointScala = schedule.minBy(_.nextValidTime(start))
}
