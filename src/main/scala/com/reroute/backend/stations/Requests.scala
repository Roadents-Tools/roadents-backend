package com.reroute.backend.stations

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{Station, StationWithRoute}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

case class ArrivableRequest(station: StationWithRoute, starttime: TimePoint, maxdelta: TimeDelta,
                            limit: Int = Int.MaxValue)

case class TransferRequest(station: Station, distance: Distance, limit: Int = Int.MaxValue)

case class PathsRequest(station: Station, starttime: TimePoint, maxdelta: TimeDelta, limit: Int = Int.MaxValue)
