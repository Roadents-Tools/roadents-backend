package com.reroute.backend.logic.revdonut

import com.moodysalem.TimezoneMapper
import com.reroute.backend.logic.{ApplicationRequest, RequestMapper}
import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location.{DestCategory, InputLocation}
import com.reroute.backend.model.routing.{Route, TransitStep, WalkStep}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}

import scala.util.Try

class RevDonutRequest private(
                               val start: InputLocation,
                               val desttype: DestCategory,
                               val totaltime: TimeDelta,
                               val starttime: TimePoint,
                               val totalwalktime: TimeDelta,
                               val maxwalktime: TimeDelta,
                               val totalwaittime: TimeDelta,
                               val maxwaittime: TimeDelta,
                               val minwaittime: TimeDelta,
                               val mindist: Distance,
                               val steps: Int,
                               val limit: Int
                             ) extends ApplicationRequest {

  require(
    start.latitude <= 90 && start.latitude >= -90 && start.longitude <= 180 && start.longitude >= -180,
    s"Coords (${start.latitude}, ${start.longitude}) are invalid."
  )
  require(
    totaltime.seconds.abs <= RevDonutRequest.DELTA_VALUE_MAX && totaltime < TimeDelta.NULL,
    s"Total delta value ${totaltime.seconds} is out of range. Must be less than ${RevDonutRequest.DELTA_VALUE_MAX}."
  )
  require(
    maxwalktime.abs.seconds >= RevDonutRequest.WALK_MIN || maxwalktime.abs.seconds <= RevDonutRequest.WALK_MAX,
    s"Max step walk time ${maxwalktime.seconds} out of range. Must be between ${RevDonutRequest.WALK_MIN} and ${RevDonutRequest.WALK_MAX}"
  )
  require(
    steps >= RevDonutRequest.STEPS_MIN && steps <= RevDonutRequest.STEPS_MAX,
    s"Step count $steps out of range. Must be between ${RevDonutRequest.STEPS_MIN} and ${RevDonutRequest.STEPS_MAX}."
  )
  require(
    limit >= RevDonutRequest.LIMIT_MIN && limit <= RevDonutRequest.LIMIT_MAX,
    s"Limit $limit out of range. Must be between ${RevDonutRequest.LIMIT_MIN} and ${RevDonutRequest.LIMIT_MAX}."
  )
  override val tag: String = "REVDONUT"

  def meetsRequest(route: Route): Boolean = {
    //TODO: Check categories if possible.
    route.totalTime.abs <= totaltime.abs &&
      route.walkTime.abs <= totalwalktime.abs &&
      route.waitTime.abs <= totalwaittime.abs &&
      route.distance > mindist &&
      route.steps.exists({
        case stp: WalkStep => stp.totaltime.abs < maxwalktime.abs
        case stp: TransitStep => stp.waittime.abs > minwaittime.abs || stp.waittime.abs < maxwaittime.abs
      })
  }

  def effectiveWalkLeft(route: Route): TimeDelta = {
    Seq(totaltime - route.totalTime, totalwalktime - route.walkTime, maxwalktime).minBy(_.abs)
  }

  def effectiveWaitLeft(route: Route): TimeDelta = {
    Seq(totaltime - route.totalTime, totalwaittime - route.waitTime, maxwaittime).max
  }

  def endTime: TimePoint = starttime + totaltime
}

object RevDonutRequest {
  private final val LAT_KEY = "latitude"
  private final val LNG_KEY = "longitude"
  private final val QUERY_KEY = "query"
  private final val DELTA_KEY = "total_time"
  private final val TIME_KEY = "arrival_time"
  private final val TOTAL_WALK_KEY = "max_walk_time"
  private final val WALK_KEY = "max_step_walk_time"
  private final val WAIT_KEY = "max_wait_time"
  private final val STEP_WAIT_KEY = "max_step_wait_time"
  private final val MIN_STEP_WAIT_KEY = "min_step_wait_time"
  private final val MIN_DIST_KEY = "min_dist"
  private final val STEPS_KEY = "steps"
  private final val LIMIT_KEY = "limit"

  private final val WALK_DEFAULT = -300 * TimeDelta.SECOND
  private final val MIN_STEP_WAIT_DEFAULT = -60 * TimeDelta.SECOND
  private final val STEPS_DEFAULT = 5
  private final val LIMIT_DEFAULT = 50

  private final val DELTA_VALUE_MAX = 10800
  private final val WALK_MAX = 900
  private final val WALK_MIN = 60

  private final val STEPS_MIN = 1
  private final val STEPS_MAX = 10
  private final val LIMIT_MIN = 1
  private final val LIMIT_MAX = 100

  def apply(
             startPoint: InputLocation,
             desttype: DestCategory,
             maxDelta: TimeDelta,
             inpstarttime: Option[TimePoint] = None,
             totalwalktime: Option[TimeDelta] = None,
             inmaxwalktime: Option[TimeDelta] = None,
             totalwaittime: Option[TimeDelta] = None,
             maxwaittime: Option[TimeDelta] = None,
             inminwaittime: Option[TimeDelta] = None,
             mindist: Option[Distance] = None,
             insteps: Option[Int] = None,
             inlimit: Option[Int] = None
           ): RevDonutRequest = {

    val startTime = inpstarttime.getOrElse(TimePoint.now(TimezoneMapper.tzNameAt(startPoint.latitude, startPoint.longitude)))
    val totalWalkMax = totalwalktime.getOrElse(maxDelta)
    val totalWaitMax = totalwaittime.getOrElse(maxDelta)
    val stepWaitMax = maxwaittime.getOrElse(totalWaitMax)
    val minNetDist = mindist.getOrElse(Distance.NULL)
    val minwaittime = inminwaittime.getOrElse(MIN_STEP_WAIT_DEFAULT)
    val maxwalktime = inmaxwalktime.getOrElse(WALK_DEFAULT)
    val steps = insteps.getOrElse(STEPS_DEFAULT)
    val limit = inlimit.getOrElse(LIMIT_DEFAULT)

    new RevDonutRequest(
      start = startPoint,
      starttime = startTime,
      desttype = desttype,
      totaltime = maxDelta,
      totalwaittime = totalWaitMax,
      totalwalktime = totalWalkMax,
      maxwaittime = stepWaitMax,
      minwaittime = minwaittime,
      maxwalktime = maxwalktime,
      mindist = minNetDist,
      steps = steps,
      limit = limit
    )
  }

  implicit object ReqMapper extends RequestMapper[RevDonutRequest] {

    private def deltaMap(inp: String): TimeDelta = {
      //Strips all digits after the decimal using a regex
      val trunked = inp.replaceAll("\\..*", "")

      TimeDelta.SECOND * trunked.toLong.abs * -1
    }

    override def buildQuery(callArgs: Map[String, String]): Either[String, RevDonutRequest] = Try {

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
        case Some(dt) => deltaMap(dt)
        case None => return Left("Max delta not passed.")
      }

      val inpTime = callArgs.get(TIME_KEY)
        .map(_.replaceAll("\\..*", "").toLong * 1000)
        .map(TimePoint(_, TimezoneMapper.tzNameAt(lat, lng)))

      val totalWalkMax = callArgs.get(TOTAL_WALK_KEY).map(deltaMap)
      val stepWalkMax = callArgs.get(WALK_KEY).map(deltaMap)
      val totalWaitMax = callArgs.get(WAIT_KEY).map(deltaMap)
      val stepWaitMax = callArgs.get(STEP_WAIT_KEY).map(deltaMap)
      val stepWaitMin = callArgs.get(MIN_STEP_WAIT_KEY).map(deltaMap)
      val minNetDist = callArgs.get(MIN_DIST_KEY).map(dx => Distance(dx.toDouble, DistUnits.METERS))
      val stepLimit = callArgs.get(STEPS_KEY).map(_.toInt)
      val routeLimit = callArgs.get(LIMIT_KEY).map(_.toInt)

      Right(RevDonutRequest(
        startPoint = InputLocation(lat, lng),
        desttype = destQuery,
        maxDelta = maxDelta,
        inpstarttime = inpTime,
        totalwalktime = totalWalkMax,
        inmaxwalktime = stepWalkMax,
        totalwaittime = totalWaitMax,
        maxwaittime = stepWaitMax,
        inminwaittime = stepWaitMin,
        mindist = minNetDist,
        insteps = stepLimit,
        inlimit = routeLimit
      ))
    } recoverWith {
      case e: IllegalArgumentException => Try(Left(e.getMessage))
    } getOrElse Left("Unknown error occurred. Please contact Reroute for help.")
  }
}

