package com.reroute.backend.logic.utils

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.StartScala
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

case class StationRouteBuildRequestScala private(
                                                  start: StartScala,
                                                  starttime: TimePointScala,
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
}

case class DistanceLimit(
                          min: DistanceScala = DistanceScala.NULL,
                          max: DistanceScala = DistanceScala.MAX_VALUE,
                          total_min: DistanceScala = DistanceScala.NULL,
                          total_max: DistanceScala = DistanceScala.MAX_VALUE
                        )

case class TimeDeltaLimit(
                           min: TimeDeltaScala = TimeDeltaScala.NULL,
                           max: TimeDeltaScala = TimeDeltaScala.MAX_VALUE,
                           total_min: TimeDeltaScala = TimeDeltaScala.NULL,
                           total_max: TimeDeltaScala = TimeDeltaScala.MAX_VALUE
                         )
object StationRouteBuildRequestScala {
  def apply(
             start: StartScala,
             starttime: TimePointScala,
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

    require(delta.total_max != TimeDeltaScala.MAX_VALUE)

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
        max = Seq(TimeDeltaScala.MAX_SPEED_PER_MINUTE * delta.max.minutes, distance.max).min,
        total_max = Seq(TimeDeltaScala.MAX_SPEED_PER_MINUTE * delta.total_max.minutes, distance.total_max).min,
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