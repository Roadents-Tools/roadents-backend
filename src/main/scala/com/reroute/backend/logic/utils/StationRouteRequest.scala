package com.reroute.backend.logic.utils

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, LocationPoint}
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

case class StationRouteBuildRequestScala private(
                                                  start: InputLocation,
                                                  starttime: TimePoint,
                                                  delta: TimeDeltaLimit = TimeDeltaLimit(),
                                                  distance: DistanceLimit = DistanceLimit(),
                                                  walkTime: TimeDeltaLimit = TimeDeltaLimit(),
                                                  walkDistance: DistanceLimit = DistanceLimit(),
                                                  waitTime: TimeDeltaLimit = TimeDeltaLimit(),
                                                  transferTime: TimeDeltaLimit = TimeDeltaLimit(),
                                                  transitTime: TimeDeltaLimit = TimeDeltaLimit(),
                                                  stepLimit: Int = Int.MaxValue,
                                                  finallimit: Int,
                                                ) {
  private final val WALK_MODIFIER = 3
  private final val PATHS_MODIFIER = 10
  private final val TRANSIT_MODIFIER = 10

  val walkLimit: Int = if (finallimit < Int.MaxValue) WALK_MODIFIER * finallimit else Int.MaxValue
  val pathslimit: Int = if (finallimit < Int.MaxValue) PATHS_MODIFIER * finallimit else Int.MaxValue
  val transitlimit: Int = if (finallimit < Int.MaxValue) TRANSIT_MODIFIER * finallimit else Int.MaxValue


  @inline
  private def stepWorks(step: RouteStep): Boolean = step match {
    case stp: TransferWalkStep =>
      if (stp.totaltime > transferTime.max) false
      else if (stp.totaltime < transferTime.min) false
      else if (stp.totaltime > walkTime.max) false
      else if (stp.totaltime < walkTime.min) false
      else if (stp.walkdistance > walkDistance.max) false
      else if (stp.walkdistance < walkDistance.min) false
      else true
    case stp: WalkStep =>
      if (stp.totaltime > walkTime.max) false
      else if (stp.totaltime < walkTime.min) false
      else if (stp.walkdistance > walkDistance.max) false
      else if (stp.walkdistance < walkDistance.min) false
      else true
    case stp: TransitStep =>
      if (stp.waittime > waitTime.max) false
      else if (stp.waittime < waitTime.min) false
      else if (stp.traveltime > transitTime.max) false
      else if (stp.traveltime < transitTime.min) false
      else true
    case _ => true
  }

  def routeValid(route: Route): Boolean = {
    if (route.distance / route.totalTime.hours < LocationPoint.AVG_WALKING_PER_HOUR) return false

    else if (route.steps.lengthCompare(stepLimit) > 0) return false
    else if (route.distance > distance.total_max) return false

    var walktotal = TimeDelta.NULL
    var walkdisttotal = Distance.NULL
    var transfertotal = TimeDelta.NULL
    var transittotal = TimeDelta.NULL
    var waittotal = TimeDelta.NULL
    var total = TimeDelta.NULL

    for (step <- route.steps) {
      if (!stepWorks(step)) return false
      step match {
        case st: TransferWalkStep =>
          walktotal += st.totaltime
          walkdisttotal += st.walkdistance
          transfertotal += st.totaltime
          total += st.totaltime
        case st: WalkStep =>
          walktotal += st.totaltime
          walkdisttotal += st.walkdistance
          total += st.totaltime
        case st: TransitStep =>
          transittotal += st.totaltime
          waittotal += st.waittime
          total += st.totaltime
      }
      if (walktotal > walkTime.total_max) false
      else if (walkdisttotal > walkDistance.total_max) false
      else if (waittotal > waitTime.total_max) false
      else if (transfertotal > transferTime.total_max) false
      else if (transittotal > transitTime.total_max) false
      else if (total > delta.total_max) false
    }
    true
  }

  def meetsMinimums(route: Route): Boolean = {
    if (route.distance < distance.total_min) return false

    var walktotal = TimeDelta.NULL
    var walkdisttotal = Distance.NULL
    var transfertotal = TimeDelta.NULL
    var transittotal = TimeDelta.NULL
    var waittotal = TimeDelta.NULL
    var total = TimeDelta.NULL

    for (step <- route.steps) step match {
      case st: TransferWalkStep =>
        walktotal += st.totaltime
        walkdisttotal += st.walkdistance
        transfertotal += st.totaltime
        total += st.totaltime
      case st: WalkStep =>
        walktotal += st.totaltime
        walkdisttotal += st.walkdistance
        total += st.totaltime
      case st: TransitStep =>
        transittotal += st.totaltime
        waittotal += st.waittime
        total += st.totaltime
    }
    if (walktotal < walkTime.total_min) false
    else if (walkdisttotal < walkDistance.total_min) false
    else if (waittotal < waitTime.total_min) false
    else if (transfertotal < transferTime.total_min) false
    else if (transittotal < transitTime.total_min) false
    else if (total < delta.total_min) false
    else true
  }
}

case class DistanceLimit(
                          min: Distance = Distance.NULL,
                          max: Distance = Distance.MAX_VALUE,
                          total_min: Distance = Distance.NULL,
                          total_max: Distance = Distance.MAX_VALUE
                        )

case class TimeDeltaLimit(
                           min: TimeDelta = TimeDelta.NULL,
                           max: TimeDelta = TimeDelta.MAX_VALUE,
                           total_min: TimeDelta = TimeDelta.NULL,
                           total_max: TimeDelta = TimeDelta.MAX_VALUE
                         )

object StationRouteBuildRequestScala {
  def apply(
             start: InputLocation,
             starttime: TimePoint,
             delta: TimeDeltaLimit = TimeDeltaLimit(),
             distance: DistanceLimit = DistanceLimit(),
             walkTime: TimeDeltaLimit = TimeDeltaLimit(),
             walkDistance: DistanceLimit = DistanceLimit(),
             waitTime: TimeDeltaLimit = TimeDeltaLimit(),
             transferTime: TimeDeltaLimit = TimeDeltaLimit(),
             transitTime: TimeDeltaLimit = TimeDeltaLimit(),
             stepLimit: Int = Int.MaxValue,
             finallimit: Int,
           ): StationRouteBuildRequestScala = {

    require(delta.total_max != TimeDelta.MAX_VALUE)

    new StationRouteBuildRequestScala(
      start = start,
      starttime = starttime,
      delta = TimeDeltaLimit(
        max = delta.total_max,
        total_max = delta.total_max,
        min = delta.total_min,
        total_min = delta.total_min
      ),
      distance = DistanceLimit(
        max = Seq(TimeDelta.MAX_SPEED_PER_MINUTE * delta.max.minutes, distance.max).min,
        total_max = Seq(TimeDelta.MAX_SPEED_PER_MINUTE * delta.total_max.minutes, distance.total_max).min,
        min = distance.min,
        total_min = distance.total_min
      ),
      walkTime = TimeDeltaLimit(
        max = Seq(walkTime.max, walkTime.total_max, delta.total_max, walkDistance.total_max.avgWalkTime, walkDistance.max.avgWalkTime).min,
        total_max = Seq(walkTime.total_max, delta.total_max, walkDistance.total_max.avgWalkTime).min,
        min = walkTime.min,
        total_min = walkTime.total_min
      ),
      walkDistance = DistanceLimit(
        max = Seq(walkDistance.max, walkDistance.total_max, walkTime.max.avgWalkDist, walkTime.total_max.avgWalkDist, delta.total_max.avgWalkDist).min,
        total_max = Seq(walkDistance.total_max, walkTime.total_max.avgWalkDist, delta.total_max.avgWalkDist).min,
        min = walkDistance.min,
        total_min = walkDistance.total_min
      ),
      waitTime = TimeDeltaLimit(
        max = Seq(waitTime.max, waitTime.total_max, delta.total_max).min,
        total_max = Seq(waitTime.total_max, delta.total_max).min,
        min = waitTime.min,
        total_min = waitTime.total_min
      ),
      transferTime = TimeDeltaLimit(
        max = Seq(transferTime.max, transferTime.total_max, walkTime.max, walkTime.total_max, walkDistance.total_max.avgWalkTime, walkDistance.max.avgWalkTime, delta.total_max).min,
        total_max = Seq(transferTime.total_max, walkTime.total_max, walkDistance.total_max.avgWalkTime, delta.total_max).min,
        min = transferTime.min,
        total_min = transferTime.total_min
      ),
      transitTime = TimeDeltaLimit(
        max = Seq(transitTime.total_max, transferTime.max, delta.total_max).min,
        total_max = Seq(transitTime.total_max, delta.total_max).min,
        min = transitTime.min,
        total_min = transitTime.total_min
      ),
      stepLimit,
      finallimit
    )
  }
}