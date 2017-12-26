package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseIDScala
import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePointScala, TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationDatabaseScala
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}

class TestStationDbScala extends StationDatabaseScala {
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

  override def getStartingStations(start: StartScala, dist: DistanceScala, limit: Int): List[StationScala] = {
    genRawStations(start, dist).take(limit).toList
  }

  @inline
  private final def genRawStations(inpstart: LocationPointScala, inpdist: DistanceScala): Seq[StationScala] = {
    val start = StartScala(
      inpstart.latitude - inpstart.latitude % ADDITIVE,
      inpstart.longitude - inpstart.longitude % ADDITIVE
    )
    val dist = (inpstart distanceTo start) + inpdist

    val latrange = LocationPointScala.latitudeRange(start, dist)
    val latsteprange = (latrange / ADDITIVE).toInt
    val latmin = latsteprange / -2
    val latmax = latsteprange / 2

    val lngrange = LocationPointScala.longitudeRange(start, dist)
    val lngsteprange = (lngrange / ADDITIVE).toInt
    val lngmin = lngsteprange / -2
    val lngmax = lngsteprange / 2
    val possible = for (lataddstep <- latmin to latmax; lngaddstep <- lngmin to lngmax) yield {
      buildFakeStation(start.latitude + lataddstep * ADDITIVE, start.longitude + lngaddstep * ADDITIVE)
    }
    possible.filter(st => (st distanceTo inpstart) < inpdist)
  }

  override def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, List[StationScala]] = {
    request
      .map(req => req -> getTransferStations(req.station, req.distance, req.limit))
      .toMap
  }

  def getTransferStations(station: StationScala, range: DistanceScala, limit: Int): List[StationScala] = {
    genRawStations(station, range)
      .filter(!station.overlaps(_))
      .take(limit)
      .toList
  }

  override def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, List[StationWithRoute]] = {
    request
      .map(req => req -> getPathsForStation(req.station, req.starttime, req.maxdelta, req.limit))
      .toMap
  }

  def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala, limit: Int): List[StationWithRoute] = {
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
        SchedulePointScala(
          packed % 84000,
          255.toByte,
          0,
          packed / TIME_DIFF,
          DatabaseIDScala(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
        )
      })
    if (baseScheds.isEmpty) {
      return List.empty
    }
    val routes = Seq(
      TransitPathScala("Test Agency", "Lat " + station.latitude + " PLUS", "" + startPackedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPathScala("Test Agency", "Lat " + station.latitude + " MINUS", "" + startPackedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPathScala("Test Agency", "Lng " + station.longitude + " PLUS", "" + startPackedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.longitude.hashCode())),
      TransitPathScala("Test Agency", "Lng " + station.longitude + " MINUS", "" + startPackedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.longitude.hashCode()))
    )

    routes
      .map(rt => StationWithRoute(station, rt, baseScheds.filter(_.departsWithin(starttime, maxdelta)).toList))
      .toList
  }

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, List[StationWithRoute]] = {
    request
      .map(req => req -> getArrivableStations(req.station, req.starttime, req.maxdelta, req.limit))
      .toMap
  }

  def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala, limit: Int): List[StationWithRoute] = {
    val station = start.station
    val route = start.route
    val baseSched = start.nextDepartureSched(starttime)

    val usableScheds = (0 to 86399 by TIME_DIFF)
      .map(packed => {
        SchedulePointScala(
          packedTime = packed,
          validDays = 255.toByte,
          fuzz = 0,
          index = packed / TIME_DIFF,
          id = DatabaseIDScala(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
        )
      })
      .filter(_.arrivesWithin(starttime, maxDelta))

    val rval =
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

    val expectedRange = maxDelta.seconds / TIME_DIFF * ADDITIVE
    val actualRange = (start :: rval).map(dt => Math.max(Math.abs(dt.station.latitude - start.station.latitude), Math.abs(dt.station.longitude - start.station.longitude))).max
    rval
  }

  override def servesPoint(point: LocationPointScala): Boolean = {
    //point.latitude > LAT_START && point.latitude < LAT_END && point.longitude > LNG_START && point.longitude < LNG_END
    true
  }

  override def close(): Unit = {}

  override def isUp: Boolean = true

  @inline
  private final def buildFakeStation(lat: Double, lng: Double): StationScala = {
    val id = lat.hashCode() * 31 + lng.hashCode()
    StationScala("Test Station " + id, lat, lng, DatabaseIDScala(databaseName, id))
  }

  override def servesArea(point: LocationPointScala, range: DistanceScala): Boolean = {
    val maxlat = point.latitude + LocationPointScala.latitudeRange(point, range / 2)
    val minlat = point.latitude - LocationPointScala.latitudeRange(point, range / 2)
    val maxlng = point.longitude + LocationPointScala.longitudeRange(point, range / 2)
    val minlng = point.longitude - LocationPointScala.longitudeRange(point, range / 2)
    //maxlat < LAT_END && minlat > LAT_START && minlng > LNG_START && maxlng < LNG_END
    true
  }
}
