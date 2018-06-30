package com.reroute.backend.logic.finderdemo

import com.moodysalem.TimezoneMapper
import com.reroute.backend.logic.{ApplicationRequest, RequestMapper}
import com.reroute.backend.model.location.{DestCategory, InputLocation}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

class FinderDemoRequest(
                         val starta: InputLocation,
                         val startb: InputLocation,
                         val starttime: TimePoint,
                         val maxdelta: TimeDelta,
                         val query: DestCategory) extends ApplicationRequest {
  override val tag: String = "FINDER_DEMO"

  require(
    starta.latitude <= 90 && starta.latitude >= -90 && starta.longitude <= 180 && starta.longitude >= -180,
    s"Coords (${starta.latitude}, ${starta.longitude}) are invalid."
  )
  require(
    startb.latitude <= 90 && startb.latitude >= -90 && startb.longitude <= 180 && startb.longitude >= -180,
    s"Coords (${startb.latitude}, ${startb.longitude}) are invalid."
  )
}

object FinderDemoRequest {
  private final val LAT1_KEY = "lat1"
  private final val LNG1_KEY = "lng1"
  private final val LAT2_KEY = "lat2"
  private final val LNG2_KEY = "lng2"
  private final val QUERY_KEY = "type"
  private final val DELTA_KEY = "timedelta"
  private final val TIME_KEY = "starttime"

  def apply(starta: InputLocation, startb: InputLocation, starttime: TimePoint, maxdelta: TimeDelta,
            query: DestCategory): FinderDemoRequest = {
    new FinderDemoRequest(starta, startb, starttime, maxdelta, query)
  }

  implicit object ReqMapper extends RequestMapper[FinderDemoRequest] {
    override def buildQuery(callArgs: Map[String, String]): Either[String, FinderDemoRequest] = {

      //Parse required args
      val lat1 = callArgs.get(LAT1_KEY) match {
        case Some(l) => l.toDouble
        case None => return Left("Latitude1 not passed.")
      }
      val lng1 = callArgs.get(LNG1_KEY) match {
        case Some(l) => l.toDouble
        case None => return Left("Longitude1 not passed.")
      }

      val lat2 = callArgs.get(LAT2_KEY) match {
        case Some(l) => l.toDouble
        case None => return Left("Latitude2 not passed.")
      }
      val lng2 = callArgs.get(LNG2_KEY) match {
        case Some(l) => l.toDouble
        case None => return Left("Longitude2 not passed.")
      }

      val destQuery = callArgs.get(QUERY_KEY) match {
        case Some(q) => DestCategory(q)
        case None => return Left("ReturnedLocation not passed.")
      }

      val maxDelta = callArgs.get(DELTA_KEY) match {
        case Some(dt) => dt.toLong * TimeDelta.SECOND
        case None => return Left("Max delta not passed.")
      }

      val timeZone = TimezoneMapper.tzNameAt(lat1, lng1)
      val inpTime = callArgs.get(TIME_KEY)
        .map(_.toLong * 1000)
        .map(TimePoint(_, timeZone))
        .getOrElse(TimePoint.now(timeZone))

      Right(FinderDemoRequest(InputLocation(lat1, lng1), InputLocation(lat2, lng2), inpTime, maxDelta, destQuery))
    }
  }

}
