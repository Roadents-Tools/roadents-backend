package com.reroute.backend.logic.stationroute

import com.reroute.backend.logic.ApplicationRequest
import com.reroute.backend.model.location.InputLocation
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}


case class StationRouteRequest(
                                start: InputLocation,
                                starttime: TimePoint,
                                yieldLimit: Int,
                                branchLimit: Int,
                                yieldFilter: Route => Boolean,
                                branchFilter: Route => Boolean,
                                queryGenerator: StationQueryBuilder
                              ) extends ApplicationRequest {
  override val tag: String = "STATION_ROUTE"
}



object StationRouteRequest {
  def maxDeltaOnly(start: InputLocation, starttime: TimePoint, maxdelta: TimeDelta, limit: Int): StationRouteRequest = {
    val yieldFilter: Route => Boolean = route => {
      route.totalTime <= maxdelta
    }
    val branchFilter: Route => Boolean = route => {
      route.totalTime <= maxdelta
    }
    StationRouteRequest(start, starttime, limit, 2 * limit, yieldFilter, branchFilter, StationQueryBuilder.simpleBuilder(maxdelta, limit))
  }
}