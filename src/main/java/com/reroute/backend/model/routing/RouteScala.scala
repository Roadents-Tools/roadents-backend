package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{DestinationScala, LocationPointScala, StartScala}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

class RouteScala(val start: StartScala, val starttime: TimePointScala, val steps: List[RouteStepScala] = List()) {

  val dest: Option[DestinationScala] = steps.collectFirst({
    case stp if stp.endpt.isInstanceOf[DestinationScala] => stp.endpt.asInstanceOf[DestinationScala]
  })

  def distance: DistanceScala = start.distanceTo(currentEnd)

  def travelDistance: DistanceScala = steps.view.map(step => step.startpt.distanceTo(step.endpt)).fold(DistanceScala.NULL)(_ + _)

  def addStep(step: RouteStepScala): RouteScala = {
    require(currentEnd == step.startpt, s"Route is discontinuous. Tried adding ${step.startpt} to $currentEnd")
    require(dest.isEmpty, s"Route is already finished!")
    require(!hasPoint(step.endpt), s"Already have location ${step.endpt} in the route.")
    require(step.totaltime > TimeDeltaScala.NULL || step.startpt.overlaps(step.endpt), s"Teleported from ${step.startpt} to ${step.endpt}")
    new RouteScala(start, starttime, step :: steps)
  }

  def hasPoint(point: LocationPointScala): Boolean = steps.exists(_.endpt.overlaps(point))

  def +(step: RouteStepScala): RouteScala = addStep(step)

  def currentEnd: LocationPointScala = if (steps.nonEmpty) steps.head.endpt else start

  def endTime: TimePointScala = starttime + totalTime

  def totalTime: TimeDeltaScala = steps.view.map(_.totaltime).fold(TimeDeltaScala.NULL)(_ + _)

  def endTimeAt(index: Int): TimePointScala = {
    starttime + totalTimeAt(index)
  }

  def totalTimeAt(index: Int): TimeDeltaScala = {
    steps.view
      .take(index)
      .map(_.totaltime)
      .fold(TimeDeltaScala.NULL)(_ + _)
  }

  def walkTime: TimeDeltaScala = steps.view.map({
    case walking: WalkStepScala => walking.totaltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def walkTimeAt(index: Int): TimeDeltaScala = steps.view.take(index).map({
    case walking: WalkStepScala => walking.totaltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def waitTime: TimeDeltaScala = steps.view.map({
    case transit: TransitStepScala => transit.waittime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def waitTimeAt(index: Int): TimeDeltaScala = steps.view.take(index).map({
    case transit: TransitStepScala => transit.waittime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def travelTime: TimeDeltaScala = steps.view.map({
    case transit: TransitStepScala => transit.traveltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def travelTimeAt(index: Int): TimeDeltaScala = steps.view.take(index).map({
    case transit: TransitStepScala => transit.traveltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)


  override def toString = s"RouteScala($start, $starttime, $dest, $steps)"
}
