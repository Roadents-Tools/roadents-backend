package com.reroute.backend.locations

import com.reroute.backend.model.location.{DestCategory, LocationPoint}
import com.reroute.backend.model.time.TimeDelta

case class LocationsRequest(center: LocationPoint, timeRange: TimeDelta, searchquery: DestCategory,
                            limit: Int = Int.MaxValue)
