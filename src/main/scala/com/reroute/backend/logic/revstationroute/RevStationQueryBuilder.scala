package com.reroute.backend.logic.revstationroute

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{InputLocation, Station, StationWithRoute}
import com.reroute.backend.model.routing.Route
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

case class RevStationQueryBuilder(
                                   genStartQuery: InputLocation => (InputLocation, Distance, Int),
                                   genTransferQuery: (Route, Int) => TransferRequest,
                                   genPathsQuery: (Route, Int) => PathsRequest,
                                   genDepartableQuery: (Route, StationWithRoute, Int) => ArrivableRequest
                                 )

object RevStationQueryBuilder {

  private case class StandardGeneratorBase[T](item: T)(implicit converter: RevStandardQuerying[T]) {
    def limit: Int = converter.limit(item)

    def effectiveWalkLeft(route: Route): TimeDelta = converter.effectiveWalkLeft(item, route)

    def effectiveWaitLeft(route: Route): TimeDelta = converter.effectiveWaitLeft(item, route)

    def starttime: TimePoint = converter.starttime(item)

    def totaltime: TimeDelta = converter.totaltime(item)
  }

  def standardBuilder[T: RevStandardQuerying](base: T,
                                              walk_modifier: Double = 1.0,
                                              paths_modifier: Double = 1.0,
                                              arrivable_modifier: Double = 1.0
                                             ): RevStationQueryBuilder = {
    val request = StandardGeneratorBase(base)
    RevStationQueryBuilder(
      genStartQuery = start => (
        start,
        request.effectiveWalkLeft(new Route(start, request.starttime)).avgWalkDist,
        (request.limit * walk_modifier).toInt
      ),
      genTransferQuery = (rt, curlayer) => TransferRequest(
        rt.currentEnd.asInstanceOf[Station],
        request.effectiveWalkLeft(rt).avgWalkDist,
        (request.limit / curlayer * walk_modifier).toInt
      ),
      genPathsQuery = (rt, curlayer) => PathsRequest(
        rt.currentEnd.asInstanceOf[Station],
        rt.endTime,
        request.effectiveWaitLeft(rt),
        (request.limit / curlayer * paths_modifier).toInt
      ),
      genDepartableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.prevArrival(rt.endTime),
        request.totaltime + data.prevArrival(rt.endTime).timeUntil(rt.endTime),
        (request.limit / curlayer * arrivable_modifier).toInt
      )
    )
  }

  def simpleBuilder(maxdelta: TimeDelta, limit: Int): RevStationQueryBuilder = {
    RevStationQueryBuilder(
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

trait RevStandardQuerying[T] {
  def limit(request: T): Int

  def effectiveWalkLeft(request: T, route: Route): TimeDelta

  def effectiveWaitLeft(request: T, route: Route): TimeDelta

  def starttime(request: T): TimePoint

  def totaltime(request: T): TimeDelta
}
