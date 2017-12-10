package com.reroute.backend.stations

import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location.{StationScala, StationWithRoute}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

case class ArrivableRequest(station: StationWithRoute, starttime: TimePointScala, maxdelta: TimeDeltaScala)

case class TransferRequest(station: StationScala, distance: DistanceScala)

case class PathsRequest(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala)
