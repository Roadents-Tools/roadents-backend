package com.reroute.backend.logic.stationroute

import com.reroute.backend.logic.ApplicationRequest
import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, Station, StationWithRoute}
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}


case class StationRouteRequest(
                                start: InputLocation,
                                starttime: TimePoint,
                                yieldLimit: Int,
                                branchLimit: Int,
                                yieldFilter: Route => Boolean,
                                branchFilter: Route => Boolean,
                                queryGenerator: StationQueryGenerator
                              ) extends ApplicationRequest {
  override val tag: String = "STATION_ROUTE"
}

case class StationQueryGenerator(
                                  genStartQuery: InputLocation => (InputLocation, Distance, Int),
                                  genTransferQuery: (Route, Int) => TransferRequest,
                                  genPathsQuery: (Route, Int) => PathsRequest,
                                  genArrivableQuery: (Route, StationWithRoute, Int) => ArrivableRequest
                                )

object StationRouteRequest {
  def maxDeltaOnly(start: InputLocation, starttime: TimePoint, maxdelta: TimeDelta, limit: Int): StationRouteRequest = {
    val yieldFilter: Route => Boolean = route => {
      route.totalTime <= maxdelta
    }
    val branchFilter: Route => Boolean = route => {
      route.totalTime <= maxdelta
    }
    StationRouteRequest(start, starttime, limit, 2 * limit, yieldFilter, branchFilter, simpleGenerator(maxdelta, limit))
  }

  def simpleGenerator(maxdelta: TimeDelta, limit: Int): StationQueryGenerator = {
    StationQueryGenerator(
      genStartQuery = start => (
        start,
        maxdelta.avgWalkDist,
        limit * 3
      ),
      genTransferQuery = (rt, curlayer) => TransferRequest(
        rt.currentEnd.asInstanceOf[Station],
        (maxdelta - rt.totalTime).avgWalkDist,
        (10.0 / curlayer * limit).toInt
      ),
      genPathsQuery = (rt, curlayer) => PathsRequest(
        rt.currentEnd.asInstanceOf[Station],
        rt.endTime,
        maxdelta - rt.totalTime,
        (10.0 / curlayer * limit).toInt
      ),
      genArrivableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.nextDeparture(rt.endTime),
        maxdelta - rt.starttime.timeUntil(data.nextDeparture(rt.endTime)),
        (10.0 / curlayer * limit).toInt
      )
    )
  }
}