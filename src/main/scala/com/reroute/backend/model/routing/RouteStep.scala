package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

trait RouteStep {
  type startType <: LocationPoint
  type endType <: LocationPoint
  val startpt: startType
  val endpt: endType
  val steptype: String
  val totaltime: TimeDelta
}

trait WalkStep extends RouteStep {
  val walkdistance: Distance = startpt.distanceTo(endpt)
}

case class StartWalkStep(
                          override val startpt: InputLocation,
                          override val endpt: Station,
                          override val totaltime: TimeDelta
                        ) extends WalkStep {
  override type startType = InputLocation
  override type endType = Station
  override val steptype = "start_walk"
}

case class TransferWalkStep(override val startpt: Station, override val endpt: Station,
                            override val totaltime: TimeDelta) extends WalkStep {
  override type startType = Station
  override type endType = Station
  override val steptype = "transfer_walk"
}

case class DestinationWalkStep(override val startpt: Station, override val endpt: ReturnedLocation,
                               override val totaltime: TimeDelta) extends WalkStep {
  override type startType = Station
  override type endType = ReturnedLocation
  override val steptype = "destination_walk"
}

case class FullRouteWalkStep(override val startpt: InputLocation, override val endpt: ReturnedLocation,
                             override val totaltime: TimeDelta) extends WalkStep {
  override type startType = InputLocation
  override type endType = ReturnedLocation
  override val steptype = "full_route_walk"
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
                              override val steptype: String = "misc_transit",
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
  override val steptype: String = startData.route.transitType
  override val traveltime: TimeDelta = starttime timeUntil endSched.nextValidTime(starttime + TimeDelta.SECOND)
  override val totaltime: TimeDelta = waittime + traveltime
  override val stops: Int = {
    if (startSched.index < endSched.index) endSched.index - startSched.index
    else endSched.index + startData.route.size - startSched.index
  }
}
