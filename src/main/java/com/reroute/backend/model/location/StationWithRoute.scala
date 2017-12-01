package com.reroute.backend.model.location

import com.reroute.backend.model.time.{SchedulePointScala, TimePointScala}

case class StationWithRoute(station: StationScala, route: TransitPathScala, schedule: List[SchedulePointScala]) {

  def nextArrival(start: TimePointScala): TimePointScala = schedule.view.map(_.nextValidTime(start)).min

}
