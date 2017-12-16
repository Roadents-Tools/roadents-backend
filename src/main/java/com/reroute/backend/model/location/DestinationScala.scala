package com.reroute.backend.model.location

case class DestinationScala(
                             name: String,
                             override val latitude: Double,
                             override val longitude: Double,
                             types: Seq[DestCategory]
                           ) extends LocationPointScala

object DestinationScala {
  def fromJava(orig: DestinationLocation): DestinationScala = {
    DestinationScala(orig.getName, orig.getCoordinates()(0), orig.getCoordinates()(1), List(orig.getType))
  }
}
