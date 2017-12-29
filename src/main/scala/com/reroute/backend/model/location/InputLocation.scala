package com.reroute.backend.model.location

case class InputLocation(override val latitude: Double, override val longitude: Double) extends LocationPoint
