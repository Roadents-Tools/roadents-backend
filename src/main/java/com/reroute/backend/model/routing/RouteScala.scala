package com.reroute.backend.model.routing

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{DestinationScala, LocationPointScala, StartScala}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

import scala.collection.mutable

class RouteScala(val start: StartScala, val starttime: TimePointScala) {

  private val stepsList: mutable.MutableList[RouteStepScala] = mutable.MutableList[RouteStepScala]()
  private var destOpt: Option[DestinationScala] = None

  def dest: Option[DestinationScala] = destOpt

  def distance: DistanceScala = start.distanceTo(currentEnd)

  def travelDistance: DistanceScala = steps.view.map(step => step.startpt.distanceTo(step.endpt)).reduce(_ + _)

  def steps: List[RouteStepScala] = stepsList.toList

  def addStep(step: RouteStepScala): RouteScala = {
    require(currentEnd == step.startpt, s"Route is discontinuous. Tried adding ${step.startpt} to $currentEnd")
    require(destOpt.isEmpty, s"Route already has destination node!")
    require(!stepsList.exists(_.endpt.overlaps(step.endpt)), s"Already have location ${step.endpt} in the route.")
    step.endpt match {
      case ndest: DestinationScala => destOpt = Some(ndest)
    }
    stepsList += step
    this
  }

  def currentEnd: LocationPointScala = if (stepsList.nonEmpty) stepsList.last.endpt else start

  def endTime: TimePointScala = starttime + totalTime

  def totalTime: TimeDeltaScala = stepsList.view.map(_.totaltime).reduce(_ + _)

  def copyAt(index: Int): RouteScala = {
    if (index >= stepsList.size + 1) return copy()
    val rval = new RouteScala(start, starttime)
    stepsList.view.take(index).foreach(rval.stepsList += _)
    rval
  }

  def copy(): RouteScala = {
    val rval = new RouteScala(start, starttime)
    rval.stepsList ++= stepsList
    rval.destOpt = destOpt
    rval
  }

  def endTimeAt(index: Int): TimePointScala = {
    starttime + totalTimeAt(index)
  }

  def totalTimeAt(index: Int): TimeDeltaScala = {
    stepsList.view
      .take(index)
      .map(_.totaltime)
      .fold(TimeDeltaScala.NULL)(_ + _)
  }

  def walkTime: TimeDeltaScala = stepsList.view.map({
    case walking: WalkStepScala => walking.totaltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def walkTimeAt(index: Int): TimeDeltaScala = stepsList.view.take(index).map({
    case walking: WalkStepScala => walking.totaltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def waitTime: TimeDeltaScala = stepsList.view.map({
    case transit: TransitStepScala => transit.waittime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def waitTimeAt(index: Int): TimeDeltaScala = stepsList.view.take(index).map({
    case transit: TransitStepScala => transit.waittime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def travelTime: TimeDeltaScala = stepsList.view.map({
    case transit: TransitStepScala => transit.traveltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)

  def travelTimeAt(index: Int): TimeDeltaScala = stepsList.view.take(index).map({
    case transit: TransitStepScala => transit.traveltime
    case _ => TimeDeltaScala.NULL
  }).fold(TimeDeltaScala.NULL)(_ + _)
}
