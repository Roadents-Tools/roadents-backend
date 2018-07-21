package com.reroute.backend.logic.stationroute

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.model.routing.Route
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}

case class StationQueryBuilder(
                                genWalkableQuery: (Route, Int) => WalkableRequest,
                                genArrivableQuery: (Route, StationWithRoute, Int) => ArrivableRequest
                              )

object StationQueryBuilder {

  def simpleBuilder(maxdelta: TimeDelta, limit: Int): StationQueryBuilder = {
    StationQueryBuilder(
      genWalkableQuery = (rt, curlayer) => WalkableRequest(
        rt.currentEnd,
        (maxdelta - rt.totalTime).avgWalkDist,
        rt.endTime,
        maxdelta - rt.totalTime,
        if (curlayer > 0) (10.0 / curlayer * limit).toInt else limit * 3
      ),
      genArrivableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.nextDeparture(rt.endTime),
        maxdelta - rt.starttime.timeUntil(data.nextDeparture(rt.endTime)),
        (10.0 / curlayer * limit).toInt
      )
    )
  }

  def standardBuilder[T: StandardQuerying](base: T,
                                           walk_modifier: Double = 1.0,
                                           paths_modifier: Double = 1.0,
                                           arrivable_modifier: Double = 1.0): StationQueryBuilder = {

    val request = StandardGeneratorBase(base)
    StationQueryBuilder(
      genArrivableQuery = (rt, data, curlayer) => ArrivableRequest(
        data,
        data.nextDeparture(rt.endTime),
        data.nextDeparture(rt.endTime).timeUntil(request.starttime + request.totaltime),
        (request.limit / curlayer * arrivable_modifier).toInt
      ),
      genWalkableQuery = (rt, curlayer) => WalkableRequest(
        rt.currentEnd,
        request.effectiveWalkLeft(rt).avgWalkDist,
        rt.endTime,
        request.effectiveWalkLeft(rt) + request.effectiveWaitLeft(rt)
      )
    )
  }

  private case class StandardGeneratorBase[T](item: T)(implicit converter: StandardQuerying[T]) {
    def limit: Int = converter.limit(item)

    def effectiveWalkLeft(route: Route): TimeDelta = converter.effectiveWalkLeft(item, route)

    def effectiveWaitLeft(route: Route): TimeDelta = converter.effectiveWaitLeft(item, route)

    def starttime: TimePoint = converter.starttime(item)

    def totaltime: TimeDelta = converter.totaltime(item)
  }

}

trait StandardQuerying[T] {
  def limit(request: T): Int

  def effectiveWalkLeft(request: T, route: Route): TimeDelta

  def effectiveWaitLeft(request: T, route: Route): TimeDelta

  def starttime(request: T): TimePoint

  def totaltime(request: T): TimeDelta
}
