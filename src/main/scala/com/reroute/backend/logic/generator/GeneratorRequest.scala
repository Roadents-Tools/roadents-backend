package com.reroute.backend.logic.generator

import com.moodysalem.TimezoneMapper
import com.reroute.backend.logic.{ApplicationRequestScala, RequestMapper}
import com.reroute.backend.model.distance.{DistanceScala, DistanceUnitsScala}
import com.reroute.backend.model.location.{DestCategory, StartScala}
import com.reroute.backend.model.routing.{RouteScala, TransitStepScala, WalkStepScala}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}

import scala.util.Try

class GeneratorRequest private(
                                val start: StartScala,
                                val desttype: DestCategory,
                                val totaltime: TimeDeltaScala,
                                val starttime: TimePointScala,
                                val totalwalktime: TimeDeltaScala,
                                val maxwalktime: TimeDeltaScala,
                                val totalwaittime: TimeDeltaScala,
                                val maxwaittime: TimeDeltaScala,
                                val minwaittime: TimeDeltaScala,
                                val mindist: DistanceScala,
                                val steps: Int,
                                val limit: Int
                              ) extends ApplicationRequestScala {

  require(
    start.latitude <= 90 && start.latitude >= -90 && start.longitude <= 180 && start.longitude >= -180,
    s"Coords (${start.latitude}, ${start.longitude}) are invalid."
  )
  require(
    totaltime.seconds < GeneratorRequest.DELTA_VALUE_MAX && totaltime > TimeDeltaScala.NULL,
    s"Total delta value ${totaltime.seconds} is out of range. Must be less than ${GeneratorRequest.DELTA_VALUE_MAX}."
  )
  require(
    maxwalktime.seconds < GeneratorRequest.WALK_MIN || maxwalktime.seconds < GeneratorRequest.WALK_MAX,
    s"Max step walk time ${maxwalktime.seconds} out of range. Must be between ${GeneratorRequest.WALK_MIN} and ${GeneratorRequest.WALK_MAX}"
  )
  require(
    steps > GeneratorRequest.STEPS_MIN && steps < GeneratorRequest.STEPS_MAX,
    s"Step count $steps out of range. Must be between ${GeneratorRequest.STEPS_MIN} and ${GeneratorRequest.STEPS_MAX}."
  )
  require(
    limit > GeneratorRequest.LIMIT_MIN && limit < GeneratorRequest.LIMIT_MAX,
    s"Limit $limit out of range. Must be between ${GeneratorRequest.LIMIT_MIN} and ${GeneratorRequest.LIMIT_MAX}."
  )
  override val tag: String = "DONUT"

  def meetsRequest(route: RouteScala): Boolean = {
    route.start == start && route.starttime == starttime && route.dest.exists(_.types.contains(desttype)) &&
      route.totalTime <= totaltime && route.walkTime <= totalwalktime && route.waitTime <= totalwaittime &&
      route.distance >= mindist &&
      !route.steps.exists({
        case stp: WalkStepScala => stp.totaltime > maxwalktime
        case stp: TransitStepScala => stp.waittime < minwaittime || stp.waittime > maxwaittime
      })
  }
}

object GeneratorRequest extends RequestMapper[GeneratorRequest] {
  private final val LAT_KEY = "latitude"
  private final val LNG_KEY = "longitude"
  private final val QUERY_KEY = "query"
  private final val DELTA_KEY = "total_time"
  private final val TIME_KEY = "departure_time"
  private final val TOTAL_WALK_KEY = "max_walk_time"
  private final val WALK_KEY = "max_step_walk_time"
  private final val WAIT_KEY = "max_wait_time"
  private final val STEP_WAIT_KEY = "max_step_wait_time"
  private final val MIN_STEP_WAIT_KEY = "min_step_wait_time"
  private final val MIN_DIST_KEY = "min_dist"
  private final val STEPS_KEY = "steps"
  private final val LIMIT_KEY = "limit"

  private final val WALK_DEFAULT = 300 * TimeDeltaScala.SECOND
  private final val MIN_STEP_WAIT_DEFAULT = 60 * TimeDeltaScala.SECOND
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
             startPoint: StartScala,
             desttype: DestCategory,
             maxDelta: TimeDeltaScala,
             inpstarttime: Option[TimePointScala] = None,
             totalwalktime: Option[TimeDeltaScala] = None,
             inmaxwalktime: Option[TimeDeltaScala] = None,
             totalwaittime: Option[TimeDeltaScala] = None,
             maxwaittime: Option[TimeDeltaScala] = None,
             inminwaittime: Option[TimeDeltaScala] = None,
             mindist: Option[DistanceScala] = None,
             insteps: Option[Int] = None,
             inlimit: Option[Int] = None
           ): GeneratorRequest = {

    val startTime = inpstarttime.getOrElse(TimePointScala.now(TimezoneMapper.tzNameAt(startPoint.latitude, startPoint.longitude)))
    val totalWalkMax = totalwalktime.getOrElse(maxDelta)
    val totalWaitMax = totalwaittime.getOrElse(maxDelta)
    val stepWaitMax = maxwaittime.getOrElse(totalWaitMax)
    val minNetDist = mindist.getOrElse(DistanceScala.NULL)
    val minwaittime = inminwaittime.getOrElse(MIN_STEP_WAIT_DEFAULT)
    val maxwalktime = inmaxwalktime.getOrElse(WALK_DEFAULT)
    val steps = insteps.getOrElse(STEPS_DEFAULT)
    val limit = inlimit.getOrElse(LIMIT_DEFAULT)

    new GeneratorRequest(
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

  override def buildQuery(callArgs: Map[String, String]): Either[String, GeneratorRequest] = Try {

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
      case None => return Left("Destination not passed.")
    }

    val maxDelta = callArgs.get(DELTA_KEY) match {
      case Some(dt) => dt.toLong * TimeDeltaScala.SECOND
      case None => return Left("Max delta not passed.")
    }

    val inpTime = callArgs.get(TIME_KEY)
      .map(_.toLong * 1000)
      .map(TimePointScala(_, TimezoneMapper.tzNameAt(lat, lng)))

    val totalWalkMax = callArgs.get(TOTAL_WALK_KEY).map(wdt => wdt.toLong * TimeDeltaScala.SECOND)
    val stepWalkMax = callArgs.get(WALK_KEY).map(wdt => wdt.toLong * TimeDeltaScala.SECOND)
    val totalWaitMax = callArgs.get(WAIT_KEY).map(wdt => wdt.toLong * TimeDeltaScala.SECOND)
    val stepWaitMax = callArgs.get(STEP_WAIT_KEY).map(wdt => wdt.toLong * TimeDeltaScala.SECOND)
    val stepWaitMin = callArgs.get(MIN_STEP_WAIT_KEY).map(wdt => wdt.toLong * TimeDeltaScala.SECOND)
    val minNetDist = callArgs.get(MIN_DIST_KEY).map(dx => DistanceScala(dx.toDouble, DistanceUnitsScala.METERS))
    val stepLimit = callArgs.get(STEPS_KEY).map(_.toInt)
    val routeLimit = callArgs.get(LIMIT_KEY).map(_.toInt)

    Right(GeneratorRequest(
      startPoint = StartScala(lat, lng),
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

