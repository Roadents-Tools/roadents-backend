package com.reroute.backend.model.location

import com.reroute.backend.model.time.{SchedulePoint, TimeDelta, TimePoint}

case class StationWithRoute(station: Station, route: TransitPath, schedule: Seq[SchedulePoint]) {

  def nextArrival(start: TimePoint): TimePoint = schedule.map(_.nextArrival(start)).min

  def nextArrivalSched(start: TimePoint): SchedulePoint = schedule.minBy(_.nextArrival(start))

  def nextDeparture(start: TimePoint): TimePoint = schedule.map(_.nextDeparture(start)).min

  def nextDepartureSched(start: TimePoint): SchedulePoint = schedule.minBy(_.nextDeparture(start))

  def departsWithin(start: TimePoint, maxdelta: TimeDelta): Boolean = {
    schedule.exists(_.departsWithin(start, maxdelta))
  }

  def prevArrival(base: TimePoint): TimePoint = schedule.map(_.prevArrival(base)).max

  def prevArrivalSched(base: TimePoint): SchedulePoint = schedule.maxBy(_.prevArrival(base))

  def prevDeparture(base: TimePoint): TimePoint = schedule.map(_.prevDeparture(base)).max

  def prevDepartureSched(base: TimePoint): SchedulePoint = schedule.maxBy(_.prevDeparture(base))

  def arrivesWithin(base: TimePoint, delta: TimeDelta): Boolean = {
    schedule.exists(_.arrivesWithin(base, delta))
  }
}
