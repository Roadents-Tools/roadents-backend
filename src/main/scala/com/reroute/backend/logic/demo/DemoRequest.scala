package com.reroute.backend.logic.demo

import com.moodysalem.TimezoneMapper
import com.reroute.backend.logic.{ApplicationRequest, RequestMapper}
import com.reroute.backend.model.location.{DestCategory, InputLocation}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

case class DemoRequest(
                        start: InputLocation,
                        starttime: TimePoint,
                        maxdelta: TimeDelta,
                        endquery: DestCategory,
                        limit: Int = DemoSorter.VALUES.length * 15
                      ) extends ApplicationRequest {
  override val tag: String = "DEMO"
}

object DemoRequest extends RequestMapper[DemoRequest] {
  private final val LAT_KEY = "latitude"
  private final val LNG_KEY = "longitude"
  private final val QUERY_KEY = "type"
  private final val DELTA_KEY = "timedelta"
  private final val TIME_KEY = "starttime"

  override def buildQuery(callArgs: Map[String, String]): Either[String, DemoRequest] = {

    //Parse required args
    val lat = callArgs.get(LAT_KEY) match {
      case Some(l) => l.toDouble
      case None => return Left("Latitude not passed.")
    }
    val lng = callArgs.get(LNG_KEY) match {
      case Some(l) => l.toDouble
      case None => return Left("Longitude not passed.")
    }

    val destQuery = callArgs.get(QUERY_KEY) match {
      case Some(q) => DestCategory(q)
      case None => return Left("ReturnedLocation not passed.")
    }

    val maxDelta = callArgs.get(DELTA_KEY) match {
      case Some(dt) => dt.toLong * TimeDelta.SECOND
      case None => return Left("Max delta not passed.")
    }

    val timeZone = TimezoneMapper.tzNameAt(lat, lng)
    val inpTime = callArgs.get(TIME_KEY)
      .map(_.toLong * 1000)
      .map(TimePoint(_, timeZone))
      .getOrElse(TimePoint.now(timeZone))

    Right(DemoRequest(InputLocation(lat, lng), inpTime, maxDelta, destQuery))
  }
}