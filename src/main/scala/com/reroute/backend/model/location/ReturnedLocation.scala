package com.reroute.backend.model.location

case class ReturnedLocation(
                             name: String,
                             override val latitude: Double,
                             override val longitude: Double,
                             types: Seq[DestCategory]
                           ) extends LocationPoint
