package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePoint, TimeDelta, TimePoint}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

class TestStationDb extends StationDatabase {
  override val databaseName: String = "TESTDB"
  private val ADDITIVE = 0.01
  private val TIME_DIFF = 150
  private val LAT_START = 37
  private val LAT_END = 38
  private val LNG_START = -123
  private val LNG_END = -121
  private val stations = for (
    latmult <- 0 to ((LAT_END - LAT_START) / ADDITIVE).toInt;
    lngmult <- 0 to ((LNG_END - LNG_START) / ADDITIVE).toInt)
    yield {
      val lat = LAT_START + ADDITIVE * latmult
      val lng = LNG_START + ADDITIVE * lngmult
      buildFakeStation(lat, lng)
    }

  override def getStartingStations(start: InputLocation, dist: Distance, limit: Int): List[Station] = {
    genRawStations(start, dist).take(limit).toList
  }

  @inline
  private final def genRawStations(inpstart: LocationPoint, inpdist: Distance): Seq[Station] = {
    val start = InputLocation(
      inpstart.latitude - inpstart.latitude % ADDITIVE,
      inpstart.longitude - inpstart.longitude % ADDITIVE
    )
    val dist = (inpstart distanceTo start) + inpdist

    val latrange = LocationPoint.latitudeRange(start, dist)
    val latsteprange = (latrange / ADDITIVE).toInt
    val latmin = latsteprange / -2
    val latmax = latsteprange / 2

    val lngrange = LocationPoint.longitudeRange(start, dist)
    val lngsteprange = (lngrange / ADDITIVE).toInt
    val lngmin = lngsteprange / -2
    val lngmax = lngsteprange / 2
    val possible = for (lataddstep <- latmin to latmax; lngaddstep <- lngmin to lngmax) yield {
      buildFakeStation(start.latitude + lataddstep * ADDITIVE, start.longitude + lngaddstep * ADDITIVE)
    }
    possible.filter(st => (st distanceTo inpstart) < inpdist)
  }

  override def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, List[Station]] = {
    request
      .map(req => req -> getTransferStations(req.station, req.distance, req.limit))
      .toMap
  }

  def getTransferStations(station: Station, range: Distance, limit: Int): List[Station] = {
    genRawStations(station, range)
      .filter(!station.overlaps(_))
      .take(limit)
      .toList
  }

  override def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    request
      .map(req => if (req.maxdelta >= TimeDelta.NULL) {
        req -> getPathsForStationPos(req.station, req.starttime, req.maxdelta, req.limit)
      } else {
        req -> getPathsForStationNeg(req.station, req.starttime, req.maxdelta, req.limit)
      })
      .toMap
  }

  def getPathsForStationPos(station: Station, starttime: TimePoint, maxdelta: TimeDelta,
                            limit: Int): List[StationWithRoute] = {
    val startPackedTime = if (starttime.packedTime % TIME_DIFF == 0) {
      starttime.packedTime
    } else {
      starttime.packedTime - starttime.packedTime % TIME_DIFF + TIME_DIFF
    }
    val endPackedTime = startPackedTime + maxdelta.seconds.toInt - maxdelta.seconds.toInt % TIME_DIFF
    val baseScheds = (startPackedTime to endPackedTime by TIME_DIFF)
      .filter(secs => {
        val pdiff = secs - starttime.packedTime
        pdiff > 0 && pdiff < maxdelta.seconds || pdiff < 0 && 86400 + pdiff < maxdelta.seconds
      })
      .map(packed => {
        SchedulePoint(
          packed % 84000,
          127.toByte,
          0,
          packed / TIME_DIFF,
          DatabaseID(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
        )
      })
    if (baseScheds.isEmpty) {
      return List.empty
    }
    val routes = Seq(
      TransitPath("Test Agency", "Lat " + station.latitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lat " + station.latitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode()))
    )

    routes
      .map(rt => StationWithRoute(station, rt, baseScheds.filter(_.departsWithin(starttime, maxdelta)).toList))
      .toList
  }

  def getPathsForStationNeg(station: Station, starttime: TimePoint, maxdelta: TimeDelta,
                            limit: Int): List[StationWithRoute] = {
    val startPackedTime = if (starttime.packedTime % TIME_DIFF == 0) {
      starttime.packedTime
    } else {
      starttime.packedTime - starttime.packedTime % TIME_DIFF
    }
    val endPackedTime = startPackedTime + maxdelta.seconds.toInt - maxdelta.seconds.toInt % TIME_DIFF + TIME_DIFF
    val baseScheds = (endPackedTime to startPackedTime by TIME_DIFF)
      .filter(
        secs => (endPackedTime < secs && secs < startPackedTime) || (endPackedTime < secs + 86400 && secs + 86400 < startPackedTime))
      .map(raw => (raw + 86400) % 86400)
      .map(packed => {
        SchedulePoint(
          packed,
          127.toByte,
          0,
          packed / TIME_DIFF,
          DatabaseID(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
        )
      })
    if (baseScheds.isEmpty) {
      return List.empty
    }
    val routes = Seq(
      TransitPath("Test Agency", "Lat " + station.latitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lat " + station.latitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " PLUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode())),
      TransitPath("Test Agency", "Lng " + station.longitude + " MINUS", "" + startPackedTime, Int.MaxValue, DatabaseID(databaseName, "chain".hashCode * 31 + station.longitude.hashCode()))
    )

    routes
      .map(StationWithRoute(station, _, baseScheds.filter(_.arrivesWithin(starttime, maxdelta)).toList))
      .toList
  }

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    request
      .map(req => if (req.maxdelta > TimeDelta.NULL) {
        req -> getArrivableStationsPos(req.station, req.starttime, req.maxdelta, req.limit)
      } else {
        req -> getArrivableStationsNeg(req.station, req.starttime, req.maxdelta, req.limit)
      })
      .toMap
  }

  def getArrivableStationsNeg(start: StationWithRoute, starttime: TimePoint, maxDelta: TimeDelta,
                              limit: Int): List[StationWithRoute] = {
    val route = start.route
    val baseSched = start.prevArrivalSched(starttime)
    val maxIndexDiff = (maxDelta.seconds / TIME_DIFF).round.toInt

    val indexFilter: Station => Boolean = st => {
      val coordDist = (st.longitude - start.station.longitude).abs + (st.latitude - start.station.latitude).abs
      val indexDist = (coordDist / ADDITIVE).round.toInt
      indexDist > 0 && indexDist <= maxIndexDiff.abs
    }

    val routeFilter: Station => Boolean = {
      if (route.route.contains("Lat") && route.route.contains("PLUS")) st => {
        st.longitude == start.station.longitude && st.latitude < start.station.latitude
      }
      else if (route.route.contains("Lat") && route.route.contains("MINUS")) st => {
        st.longitude == start.station.longitude && st.latitude > start.station.latitude
      }
      else if (route.route.contains("Lng") && route.route.contains("PLUS")) st => {
        st.latitude == start.station.latitude && st.longitude < start.station.longitude
      }
      else if (route.route.contains("Lng") && route.route.contains("MINUS")) st => {
        st.latitude == start.station.latitude && st.longitude > start.station.longitude
      }
      else _ => false
    }

    stations.view
      .filter(indexFilter)
      .filter(routeFilter)
      .take(limit)
      .map(mapArrivable(_, start, baseSched, isNegative = true))
      .toList
  }

  private def mapArrivable(st: Station, start: StationWithRoute, baseSched: SchedulePoint,
                           isNegative: Boolean): StationWithRoute = {
    val coeff = if (isNegative) -1 else 1
    val coordDist = (st.longitude - start.station.longitude).abs + (st.latitude - start.station.latitude).abs

    val indexDist = (coordDist / ADDITIVE).round.toInt * coeff
    val rawIndex = baseSched.index + indexDist
    val curIndex = if (rawIndex < 0) rawIndex + Int.MaxValue else rawIndex

    val secondsDist = indexDist * TIME_DIFF
    val curTime = (baseSched.packedTime + secondsDist) % 86400

    val curSched = baseSched.copy(
      packedTime = curTime,
      index = curIndex,
      id = DatabaseID(databaseName, curTime * 31 * 31 + st.latitude.hashCode() * 31 + st.longitude.hashCode())
    )

    StationWithRoute(st, start.route, Seq(curSched))
  }

  //TODO: Summarize and reduce code to match getArrivableStationsNeg
  def getArrivableStationsPos(start: StationWithRoute, starttime: TimePoint, maxDelta: TimeDelta,
                              limit: Int): List[StationWithRoute] = {
    val route = start.route
    val baseSched = start.nextDepartureSched(starttime)

    if (route.route.contains("Lat") && route.route.contains("PLUS")) {
      stations.view
        .filter(st => st.longitude == start.station.longitude && st.latitude > start.station.latitude)
        .filter(st => (st.latitude - start.station.latitude) / ADDITIVE * TIME_DIFF < maxDelta.seconds)
        .take(limit)
        .map(st => {
          val stationsBetween = ((st.latitude - start.station.latitude) / ADDITIVE).toInt
          val secondsBetween = stationsBetween * TIME_DIFF
          val nsched = baseSched.copy(index = baseSched.index + stationsBetween, packedTime = (baseSched.packedTime + secondsBetween) % 86400)
          StationWithRoute(st, route, List(nsched))
        })
        .toList
    }
    else if (route.route.contains("Lat") && route.route.contains("MINUS")) {
      stations.view
        .filter(st => st.longitude == start.station.longitude && st.latitude < start.station.latitude)
        .filter(st => (start.station.latitude - st.latitude) / ADDITIVE * TIME_DIFF < maxDelta.seconds)
        .take(limit)
        .map(st => {
          val stationsBetween = ((start.station.latitude - st.latitude) / ADDITIVE).toInt
          val secondsBetween = stationsBetween * TIME_DIFF
          val nsched = baseSched.copy(index = baseSched.index + stationsBetween, packedTime = (baseSched.packedTime + secondsBetween) % 86400)
          StationWithRoute(st, route, List(nsched))
        })
        .toList
    }
    else if (route.route.contains("Lng") && route.route.contains("PLUS")) {
      stations.view
        .filter(st => st.latitude == start.station.latitude && st.longitude > start.station.longitude)
        .filter(st => (st.longitude - start.station.longitude) / ADDITIVE * TIME_DIFF < maxDelta.seconds)
        .take(limit)
        .map(st => {
          val stationsBetween = ((st.longitude - start.station.longitude) / ADDITIVE).toInt
          val secondsBetween = stationsBetween * TIME_DIFF
          val nsched = baseSched.copy(index = baseSched.index + stationsBetween, packedTime = (baseSched.packedTime + secondsBetween) % 86400)
          StationWithRoute(st, route, List(nsched))
        })
        .toList
    }
    else if (route.route.contains("Lng") && route.route.contains("MINUS")) {
      stations.view
        .filter(st => st.latitude == start.station.latitude && st.longitude < start.station.longitude)
        .filter(st => (start.station.longitude - st.longitude) / ADDITIVE * TIME_DIFF < maxDelta.seconds)
        .take(limit)
        .map(st => {
          val stationsBetween = ((start.station.longitude - st.longitude) / ADDITIVE).toInt
          val secondsBetween = stationsBetween * TIME_DIFF
          val nsched = baseSched.copy(index = baseSched.index + stationsBetween, packedTime = (baseSched.packedTime + secondsBetween) % 86400)
          StationWithRoute(st, route, List(nsched))
        })
        .toList
    }
    else List.empty
  }

  override def servesPoint(point: LocationPoint): Boolean = {
    true
  }

  override def close(): Unit = {}

  override def isUp: Boolean = true

  @inline
  private final def buildFakeStation(lat: Double, lng: Double): Station = {
    val id = lat.hashCode() * 31 + lng.hashCode()
    Station("Test Station " + id, lat, lng, DatabaseID(databaseName, id))
  }

  override def servesArea(point: LocationPoint, range: Distance): Boolean = {
    true
  }
}
