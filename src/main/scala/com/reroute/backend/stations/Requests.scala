package com.reroute.backend.stations

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.{LocationPoint, StationWithRoute}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}


sealed trait StationsRequest {

}

case class ArrivableRequest(station: StationWithRoute, starttime: TimePoint, maxdelta: TimeDelta,
                            limit: Int = Int.MaxValue) extends StationsRequest

case class WalkableRequest(center: LocationPoint, maxdist: Distance, starttime: TimePoint, maxdelta: TimeDelta,
                           limit: Int = Int.MaxValue) extends StationsRequest