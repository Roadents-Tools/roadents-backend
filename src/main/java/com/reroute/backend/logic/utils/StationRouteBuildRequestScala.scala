package com.reroute.backend.logic.utils

import com.reroute.backend.model.location.StartScala
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

case class StationRouteBuildRequestScala(start: StartScala,
                                         starttime: TimePointScala,
                                         delta: TimeDeltaScala,
                                         maxwalktime: TimeDeltaScala,
                                         totalwalktime: TimeDeltaScala,
                                         maxtransfertime: TimeDeltaScala,
                                         totaltransfertime: TimeDeltaScala,
                                         maxtransittime: TimeDeltaScala,
                                         totaltransittime: TimeDeltaScala,
                                         stepcount: Int,
                                         finallimit: Int,
                                         walklayerlimit: Int,
                                         pathslimit: Int,
                                         transitlimit: Int
                                        )

object StationRouteBuildRequestScala {
  private final val WalkScale = 1.5
  private final val PathsScale = 4.0
  private final val TransitScale = 4.2

  def apply(start: StartScala, starttime: TimePointScala, delta: TimeDeltaScala,
            maxwalktime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            totalwalktime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            maxtransfertime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            totaltransfertime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            maxtransittime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            totaltransittime: TimeDeltaScala = TimeDeltaScala(Long.MaxValue),
            stepcount: Int = Int.MaxValue,
            finallimit: Int = Int.MaxValue,
            walklayerlimit: Int = Int.MaxValue,
            pathslimit: Int = Int.MaxValue,
            transitlimit: Int = Int.MaxValue
           ): StationRouteBuildRequestScala =
    new StationRouteBuildRequestScala(
      start, starttime, delta,
      maxwalktime = Array(maxwalktime, totalwalktime, delta).min,
      totalwalktime = Array(totalwalktime, delta).min,
      maxtransfertime = Array(maxwalktime, totalwalktime, maxtransfertime, totaltransfertime, delta).min,
      totaltransfertime = Array(totaltransfertime, totalwalktime, delta).min,
      maxtransittime = Array(totaltransittime, maxtransittime, delta).min,
      totaltransittime = Array(totaltransittime, delta).min,
      stepcount = stepcount,
      finallimit = finallimit,
      walklayerlimit = Math.min(walklayerlimit, (WalkScale * finallimit).toInt),
      pathslimit = Math.min(pathslimit, (PathsScale * finallimit).toInt),
      transitlimit = Math.min(transitlimit, (TransitScale * finallimit).toInt)
    )
}
