package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

trait RouteStep {
  type startType <: LocationPoint
  type endType <: LocationPoint
  val startpt: startType
  val endpt: endType
  val totaltime: TimeDelta
}

trait WalkStep extends RouteStep {
  val walkdistance: Distance = startpt.distanceTo(endpt)
}

case class GeneralWalkStep(override val startpt: LocationPoint, override val endpt: LocationPoint,
                           override val totaltime: TimeDelta) extends WalkStep {
  override type startType = LocationPoint
  override type endType = LocationPoint
}

case class TransferWalkStep(override val startpt: Station, override val endpt: Station,
                            override val totaltime: TimeDelta) extends WalkStep {
  override type startType = Station
  override type endType = Station
}

trait TransitStep extends RouteStep {
  override type startType = Station
  override type endType = Station
  val transitpath: TransitPath
  val waittime: TimeDelta
  val traveltime: TimeDelta
  val stops: Int
}

case class GeneralTransitStep(override val startpt: Station,
                              override val endpt: Station,
                              override val transitpath: TransitPath,
                              override val waittime: TimeDelta,
                              override val traveltime: TimeDelta,
                              override val stops: Int
                             ) extends TransitStep {
  override val totaltime: TimeDelta = waittime + traveltime
}

case class FromDataTransitStep(
                                startData: StationWithRoute,
                                endData: StationWithRoute,
                                starttime: TimePoint,
                                override val waittime: TimeDelta
                              ) extends TransitStep {
  private val startSched = startData.nextDepartureSched(starttime - TimeDelta.SECOND)
  private val endSched = endData.nextArrivalSched(starttime)
  override val transitpath: TransitPath = startData.route
  override val startpt: Station = startData.station
  override val endpt: Station = endData.station
  override val traveltime: TimeDelta = starttime timeUntil endSched.nextArrival(starttime + TimeDelta.SECOND)
  override val totaltime: TimeDelta = waittime + traveltime
  override val stops: Int = {
    if (startSched.index < endSched.index) endSched.index - startSched.index
    else endSched.index + startData.route.size - startSched.index
  }
}

case class RevDataTransitStep(
                               startData: StationWithRoute,
                               endData: StationWithRoute,
                               starttime: TimePoint,
                               override val waittime: TimeDelta
                             ) extends TransitStep {
  private val startSched = startData.prevArrivalSched(starttime + TimeDelta.SECOND)
  private val endSched = endData.prevDepartureSched(starttime - TimeDelta.SECOND)
  override val transitpath: TransitPath = startData.route
  override val startpt: Station = startData.station
  override val endpt: Station = endData.station
  override val traveltime: TimeDelta = -1 * (endSched.prevDeparture(starttime - TimeDelta.SECOND) timeUntil starttime)
  override val totaltime: TimeDelta = waittime + traveltime
  override val stops: Int = {
    if (endSched.index < startSched.index) endSched.index - startSched.index
    else endSched.index + startSched.index - startData.route.size
  }
}

case class PitstopStep(override val startpt: LocationPoint,
                       override val totaltime: TimeDelta
                      ) extends RouteStep {
  override type endType = LocationPoint
  override type startType = LocationPoint
  override val endpt: LocationPoint = startpt
}
