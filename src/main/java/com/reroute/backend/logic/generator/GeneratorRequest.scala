package com.reroute.backend.logic.generator

import com.reroute.backend.logic.ApplicationRequestScala
import com.reroute.backend.model.location.{DestCategory, StartScala}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

case class GeneratorRequest(
                             start: StartScala,
                             starttime: TimePointScala,
                             desttype: DestCategory,
                             totaltime: TimeDeltaScala,
                             maxwalktime: TimeDeltaScala = TimeDeltaScala.NULL,
                             totalwalktime: TimeDeltaScala = TimeDeltaScala.NULL,
                             maxtransfertime: TimeDeltaScala = TimeDeltaScala(900),
                             totaltransfertime: TimeDeltaScala = TimeDeltaScala.NULL,
                             maxtransittime: TimeDeltaScala = TimeDeltaScala.NULL,
                             totaltransittime: TimeDeltaScala = TimeDeltaScala.NULL,
                             limit: Int = 100
                           ) extends ApplicationRequestScala {
  override val tag: String = "DONUT"
}

