package com.reroute.backend.model.distance

import com.reroute.backend.model.location.{InputLocation, LocationPoint}

case class CircleArea(center: LocationPoint, range: Distance) {

  require(range >= Distance.NULL)

  def contains(other: CircleArea): Boolean = {
    center.distanceTo(other.center) + other.range <= range
  }

  def contains(pt: LocationPoint): Boolean = {
    center.distanceTo(pt) <= range
  }

  def within(other: CircleArea): Boolean = {
    center.distanceTo(other.center) + range <= other.range
  }

  private def midpoint(a: LocationPoint, b: LocationPoint): LocationPoint = {
    val dlng = b.latitude - a.latitude
    val Bx = math.cos(b.latitude) * math.cos(dlng)
    val By = math.cos(b.latitude) * math.sin(dlng)
    val normMidlat = math.cos(a.latitude) + Bx
    val latMid = math.atan2(math.sin(a.latitude) + math.sin(b.latitude), math.sqrt(normMidlat * normMidlat + By * By))
    val lonMid = a.longitude + math.atan2(By, normMidlat)
    InputLocation(latMid, lonMid)
  }

  def intersects(other: CircleArea): Boolean = {
    this.center.distanceTo(other.center) <= this.range + other.range
  }

  def intersection(other: CircleArea): CircleArea = {
    if (this.within(other)) other
    else if (this.contains(other)) this
    else {
      val ncenter = midpoint(center, other.center)
      val nrange = (range + other.range - center.distanceTo(other.center)) / 2
      val absrange = if (nrange < Distance.NULL) Distance.NULL else nrange
      CircleArea(ncenter, absrange)
    }
  }

  def union(other: CircleArea): CircleArea = {
    if (this.within(other)) other
    else if (this.contains(other)) this
    else {
      val ncenter = midpoint(center, other.center)
      val thisrange = ncenter.distanceTo(center) + range
      val otherange = ncenter.distanceTo(other.center) + other.range
      val nrange = if (thisrange < otherange) otherange else thisrange
      CircleArea(ncenter, nrange)
    }
  }
}

object CircleArea {
  def union(circles: Seq[CircleArea]): CircleArea = {
    val size = circles.size
    val (nlat, nlng) = circles
      .map(_.center)
      .foldLeft((0.0, 0.0))((sm, pt) => (sm._1 + pt.latitude / size, sm._2 + pt.longitude / size))
    val ncenter = InputLocation(nlat, nlng)
    val nrange = circles
      .map({ case CircleArea(center, range) => center.distanceTo(ncenter) + range })
      .max
    CircleArea(ncenter, nrange)
  }
}
