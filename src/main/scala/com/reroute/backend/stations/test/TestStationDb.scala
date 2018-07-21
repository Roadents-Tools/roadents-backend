package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePoint, TimeDelta, TimePoint}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}
import com.typesafe.scalalogging.Logger

class TestStationDb extends StationDatabase {

  private final val logger = Logger[TestStationDb]

  override val databaseName: String = "TESTDB"
  private val ADDITIVE = 0.01
  private val TIME_DIFF = 150

  private val CENTER = InputLocation(37.5, -122)
  private val PRECACHED_RANGE = Distance(500 * DistUnits.KILOMETERS.toMeters)
  private val CACHE = _genRawStations(CENTER, PRECACHED_RANGE)

  private def constructStationWithRouteObjects(center: LocationPoint, startTime: TimePoint, maxDelta: TimeDelta)
                                              (station: Station): List[StationWithRoute] = {

    val isNeg = maxDelta < TimeDelta.NULL
    val walkdelta = center.distanceTo(station).avgWalkTime * (if (isNeg) -1 else 1)
    val starttime = startTime + walkdelta
    val maxdelta = maxDelta - walkdelta
    val startPackedTime = starttime.packedTime - starttime.packedTime % TIME_DIFF + (if (isNeg) TIME_DIFF else 0)
    val timeRangeSize = maxdelta.abs.seconds.toInt - maxdelta.abs.seconds.toInt % TIME_DIFF
    val routes = Seq(
      TransitPath("Test Agency", "Lat " + station.latitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lat " + station.latitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode()))
    )

    val baseScheds = (0 to timeRangeSize by TIME_DIFF)
      .map(offsetMagnitude => startPackedTime + (if (isNeg) -offsetMagnitude else offsetMagnitude))
      .map(packed => (packed + 86400) % 86400)
      .map(packed => {
        SchedulePoint(
          packed,
          127.toByte,
          0,
          packed / TIME_DIFF,
          DatabaseID(databaseName, packed)
        )
      })

    val validScheds = baseScheds.filter(_.packedTime != starttime.packedTime)

    val bestSchedSeq = if (validScheds.isEmpty) Seq.empty
    else if (isNeg) Seq(validScheds.maxBy(_.prevDeparture(starttime)))
    else Seq(validScheds.minBy(_.nextArrival(starttime)))

    routes.map(StationWithRoute(station, _, bestSchedSeq)).toList
  }

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    request
      .map(req => req -> gAS(req))
      .toMap
  }

  private def gAS(req: ArrivableRequest): List[StationWithRoute] = {
    val start = req.station
    val route = start.route
    val starttime = req.starttime
    val maxDelta = req.maxdelta
    val limit = req.limit

    val baseSched = if (maxDelta < TimeDelta.NULL) start.prevArrivalSched(starttime) else start.nextDepartureSched(starttime)
    assert(baseSched.packedTime == starttime.packedTime)
    val indexRange = (maxDelta.seconds / TIME_DIFF).round.toInt.abs
    val rawr = (1 to indexRange)
      .map(idx => if (maxDelta < TimeDelta.NULL) -idx else idx)
      .map(offset => makeStationAtIndex(route, start.station, baseSched, offset))
    val rval = rawr
      .filter(_.departsWithin(starttime, maxDelta))
    rval.toList
  }

  private def makeStationAtIndex(route: TransitPath, baseStation: Station, baseSched: SchedulePoint,
                                 idxRange: Int): StationWithRoute = {
    val latMod = route.route.contains("Lat")
    val dirPlus = route.route.contains("PLUS")

    val packed = (baseSched.packedTime + idxRange * TIME_DIFF + 86400) % 86400
    val lat = if (latMod && dirPlus) baseStation.latitude + idxRange * ADDITIVE
    else if (latMod) baseStation.latitude - idxRange * ADDITIVE
    else baseStation.latitude
    val lng = if (!latMod && dirPlus) baseStation.longitude + idxRange * ADDITIVE
    else if (!latMod) baseStation.longitude - idxRange * ADDITIVE
    else baseStation.longitude

    val nstat = buildFakeStation(lat, lng)
    val nsched = SchedulePoint(packed, 127.toByte, 0, baseSched.index + idxRange, DatabaseID(databaseName, packed))
    assert(packed != baseSched.packedTime, s"Got equal packed for $baseSched and $idxRange")
    StationWithRoute(nstat, route, Seq(nsched))
  }

  override def close(): Unit = {}

  override def isUp: Boolean = true

  override def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]] = {
    request.map(req => req -> {
      val swrtFilter: StationWithRoute => Boolean = if (req.maxdelta < TimeDelta.NULL) (swrt: StationWithRoute) => {
        val tstart = req.starttime - req.center.distanceTo(swrt.station).avgWalkTime
        val tdelta = req.maxdelta + tstart.timeUntil(req.starttime)
        val rval = swrt.departsWithin(tstart, tdelta)
        rval
      } else (swrt: StationWithRoute) => {
        val tstart = req.starttime + req.center.distanceTo(swrt.station).avgWalkTime
        val tdelta = req.maxdelta - req.starttime.timeUntil(tstart)
        val rval = swrt.arrivesWithin(tstart, tdelta)
        rval
      }

      val effectiveDist = Seq(req.maxdist, req.maxdelta.avgWalkDist).min
      val rawStations = genRawStations(req.center, effectiveDist)
      if (rawStations.nonEmpty) {
        val objGenerator = constructStationWithRouteObjects(req.center, req.starttime, req.maxdelta)(_)
        val resultSpace = rawStations.filter(!_.overlaps(req.center)).flatMap(objGenerator).filter(swrtFilter)
        resultSpace
      }
      else {
        List.empty
      }
    }).toMap
  }

  @inline
  private final def _genRawStations(inpstart: LocationPoint, inpdist: Distance): Seq[Station] = {
    val start = InputLocation(
      inpstart.latitude - inpstart.latitude % ADDITIVE,
      inpstart.longitude - inpstart.longitude % ADDITIVE
    )
    val dist = (inpstart distanceTo start) + inpdist

    val latrangeraw = LocationPoint.latitudeRange(start, dist)
    val latrange = latrangeraw - latrangeraw % ADDITIVE
    val latsteps = (2.0 * latrange / ADDITIVE).toInt

    val lngrangeraw = LocationPoint.longitudeRange(start, dist)
    val lngrange = lngrangeraw - lngrangeraw % ADDITIVE
    val lngsteps = (2.0 * lngrange / ADDITIVE).toInt

    val possible = for (latidx <- 0 to latsteps; lngidx <- 0 to lngsteps) yield {
      val lat = start.latitude - latrange + (latidx * ADDITIVE)
      val lng = start.longitude - lngrange + (lngidx * ADDITIVE)
      buildFakeStation(lat, lng)
    }
    possible.filter(st => (st distanceTo inpstart) < inpdist)
  }

  @inline
  private final def genRawStations(inpstart: LocationPoint, inpdist: Distance): Seq[Station] = {
    if ((inpstart.distanceTo(CENTER) + inpdist) < PRECACHED_RANGE) {
      CACHE.view.filter(st => st.distanceTo(inpstart) < inpdist)
    }
    else {
      _genRawStations(inpstart, inpdist)
    }
  }

  @inline
  private final def buildFakeStation(lat: Double, lng: Double): Station = {
    val id = lat.hashCode() * 31 + lng.hashCode()
    Station("Test Station " + id, lat, lng, DatabaseID(databaseName, id))
  }


}
