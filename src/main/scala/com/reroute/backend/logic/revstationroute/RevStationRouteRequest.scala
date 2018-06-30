package com.reroute.backend.logic.revstationroute

import com.reroute.backend.logic.ApplicationRequest
import com.reroute.backend.model.location.InputLocation
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}


case class RevStationRouteRequest(
                                   start: InputLocation,
                                   starttime: TimePoint,
                                   yieldLimit: Int,
                                   branchLimit: Int,
                                   yieldFilter: Route => Boolean,
                                   branchFilter: Route => Boolean,
                                   queryGenerator: RevStationQueryBuilder
                                 ) extends ApplicationRequest {
  override val tag: String = "STATION_ROUTE"
}



object RevStationRouteRequest {
  def maxDeltaOnly(start: InputLocation, starttime: TimePoint, maxdelta: TimeDelta,
                   limit: Int): RevStationRouteRequest = {
    val yieldFilter: Route => Boolean = _.totalTime >= maxdelta
    val branchFilter: Route => Boolean = _.totalTime >= maxdelta
    RevStationRouteRequest(start, starttime, limit, 2 * limit, yieldFilter, branchFilter,
                           RevStationQueryBuilder.simpleBuilder(maxdelta, limit))
  }

}