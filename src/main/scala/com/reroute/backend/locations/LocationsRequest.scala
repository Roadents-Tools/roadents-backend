package com.reroute.backend.locations

import com.reroute.backend.model.location.{DestCategory, LocationPointScala}
import com.reroute.backend.model.time.TimeDeltaScala

case class LocationsRequest(center: LocationPointScala, timeRange: TimeDeltaScala, searchquery: DestCategory)
