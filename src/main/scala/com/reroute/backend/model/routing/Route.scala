package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.LocationPoint
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

class Route(val start: LocationPoint, val starttime: TimePoint, val steps: List[RouteStep] = List()) {

  def distance: Distance = start.distanceTo(currentEnd)

  def totalDistance: Distance = steps.map(step => step.startpt.distanceTo(step.endpt)).fold(Distance.NULL)(_ + _)

  def walkDistance: Distance = steps.map({
    case stp: WalkStep => stp.walkdistance
    case _ => Distance.NULL
  }).fold(Distance.NULL)(_ + _)

  def addStep(step: RouteStep): Route = {
    require(currentEnd == step.startpt, s"Route is discontinuous. Tried adding ${step.startpt} to $currentEnd")
    require(!hasPoint(step.endpt), s"Already have location ${step.endpt} in the route.")
    require(step.totaltime.abs > TimeDelta.SECOND || step.startpt.overlaps(step.endpt),
            s"Teleported from ${step.startpt} to ${step.endpt}")
    require(steps.forall(
      stp => stp.totaltime == TimeDelta.NULL || (stp.totaltime < TimeDelta.NULL) == (step.totaltime < TimeDelta.NULL)))
    new Route(start, starttime, step :: steps)
  }

  def reverse(minWait: TimeDelta): Route = {

    val base = new Route(currentEnd, endTime)
    var availableWaits: List[TimeDelta] = minWait :: steps.flatMap({
      case stp: TransitStep => Some(stp.waittime)
      case _ => None
    })
    val nsteps = steps.map({
      case stp: GeneralWalkStep => GeneralWalkStep(stp.endpt, stp.startpt, stp.totaltime * -1)
      case stp: TransferWalkStep => TransferWalkStep(stp.endpt, stp.startpt, stp.totaltime * -1)
      case stp: PitstopStep => PitstopStep(stp.startpt, stp.totaltime * -1)
      case stp: TransitStep =>
        val nwait = availableWaits.head
        availableWaits = availableWaits.tail
        GeneralTransitStep(stp.endpt, stp.startpt, stp.transitpath, nwait * -1, stp.traveltime * -1, stp.stops)
    })
    base ++ nsteps
  }

  def hasPoint(point: LocationPoint): Boolean = steps.exists(_.endpt.overlaps(point))

  def +(step: RouteStep): Route = addStep(step)

  def ++(steps: Seq[RouteStep]): Route = {
    steps.foldLeft(this)({ case (rt, stp) => rt + stp })
  }

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

  override def toString = s"Route($start, $starttime, $currentEnd, $steps)"
}
