package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, LocationPoint, ReturnedLocation}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

class Route(val start: InputLocation, val starttime: TimePoint, val steps: List[RouteStep] = List()) {

  val dest: Option[ReturnedLocation] = steps.map(_.endpt).collectFirst({ case pt: ReturnedLocation => pt })

  def distance: Distance = start.distanceTo(currentEnd)

  def totalDistance: Distance = steps.map(step => step.startpt.distanceTo(step.endpt)).fold(Distance.NULL)(_ + _)

  def walkDistance: Distance = steps.map({
    case stp: WalkStep => stp.walkdistance
    case _ => Distance.NULL
  }).fold(Distance.NULL)(_ + _)

  def addStep(step: RouteStep): Route = {
    require(currentEnd == step.startpt, s"Route is discontinuous. Tried adding ${step.startpt} to $currentEnd")
    require(dest.isEmpty, s"Route is already finished!")
    require(!hasPoint(step.endpt), s"Already have location ${step.endpt} in the route.")
    require(step.totaltime > TimeDelta.NULL || step.startpt.overlaps(step.endpt), s"Teleported from ${step.startpt} to ${step.endpt}")
    new Route(start, starttime, step :: steps)
  }

  def hasPoint(point: LocationPoint): Boolean = steps.exists(_.endpt.overlaps(point))

  def +(step: RouteStep): Route = addStep(step)

  def currentEnd: LocationPoint = if (steps.nonEmpty) steps.head.endpt else start

  def endTime: TimePoint = starttime + totalTime

  def totalTime: TimeDelta = steps.view.map(_.totaltime).fold(TimeDelta.NULL)(_ + _)

  def walkTime: TimeDelta = steps.view.map({
    case walking: WalkStep => walking.totaltime
    case _ => TimeDelta.NULL
  }).fold(TimeDelta.NULL)(_ + _)

  def waitTime: TimeDelta = steps.view.map({
    case transit: TransitStep => transit.waittime
    case _ => TimeDelta.NULL
  }).fold(TimeDelta.NULL)(_ + _)

  def travelTime: TimeDelta = steps.view.map({
    case transit: TransitStep => transit.traveltime
    case _ => TimeDelta.NULL
  }).fold(TimeDelta.NULL)(_ + _)

  def transferTime: TimeDelta = steps.view.map({
    case transfer: TransferWalkStep => transfer.totaltime
    case _ => TimeDelta.NULL
  }).fold(TimeDelta.NULL)(_ + _)

  override def toString = s"Route($start, $starttime, $dest, $steps)"
}
