package com.reroute.backend.logic.revstationroute

import com.reroute.backend.logic.ApplicationRequest
import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, Station, StationWithRoute}
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}


case class RevStationRouteRequest(
                                   start: InputLocation,
                                   starttime: TimePoint,
                                   yieldLimit: Int,
                                   branchLimit: Int,
                                   yieldFilter: Route => Boolean,
                                   branchFilter: Route => Boolean,
                                   queryGenerator: RevStationQueryGenerator
                                 ) extends ApplicationRequest {
  override val tag: String = "STATION_ROUTE"
}

case class RevStationQueryGenerator(
                                     genStartQuery: InputLocation => (InputLocation, Distance, Int),
                                     genTransferQuery: (Route, Int) => TransferRequest,
                                     genPathsQuery: (Route, Int) => PathsRequest,
                                     genDepartableQuery: (Route, StationWithRoute, Int) => ArrivableRequest
                                   )

object RevStationRouteRequest {
  def maxDeltaOnly(start: InputLocation, starttime: TimePoint, maxdelta: TimeDelta,
                   limit: Int): RevStationRouteRequest = {
    val yieldFilter: Route => Boolean = _.totalTime >= maxdelta
    val branchFilter: Route => Boolean = _.totalTime >= maxdelta
    RevStationRouteRequest(start, starttime, limit, 2 * limit, yieldFilter, branchFilter, simpleGenerator(maxdelta, limit))
  }

  def simpleGenerator(maxdelta: TimeDelta, limit: Int): RevStationQueryGenerator = {
    RevStationQueryGenerator(
      genStartQuery = start => (
        start,
        maxdelta.abs.avgWalkDist,
        limit * 3
      ),
      genTransferQuery = (rt, curlayer) => TransferRequest(
        rt.currentEnd.asInstanceOf[Station],
        (maxdelta - rt.totalTime).abs.avgWalkDist,
        (10.0 / curlayer * limit).toInt
      ),
      genPathsQuery = (rt, curlayer) => PathsRequest(
        rt.currentEnd.asInstanceOf[Station],
        rt.endTime,
        maxdelta - rt.totalTime,
        (10.0 / curlayer * limit).toInt
      ),
      genDepartableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.prevArrival(rt.endTime),
        maxdelta + data.prevArrival(rt.endTime).timeUntil(rt.endTime),
        (10.0 / curlayer * limit).toInt
      )
    )
  }
}