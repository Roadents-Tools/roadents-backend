package com.reroute.backend.model.location

case class DestCategory(category: String) extends AnyVal

object DestCategory {
  implicit def fromJava(orig: LocationType): DestCategory = DestCategory(orig.getEncodedname)
}
