package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseIDScala
import com.reroute.backend.model.distance.DistanceScala
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePointScala, TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.interfaces.StationDatabaseScala

class TestStationDbScala extends StationDatabaseScala {
  private val stations = (for (latmult <- 0 to 50; lngmult <- 0 to 100) yield {
    val lat = 37 + 0.02 * latmult
    val lng = -123 +.02 * lngmult
    buildFakeStation(lat, lng)
  }).toList

  override def getStartingStations(start: StartScala, dist: DistanceScala): List[StationScala] = genRawStations(start, dist)

  @inline
  private final def genRawStations(start: LocationPointScala, dist: DistanceScala): List[StationScala] = {
    stations.filter(st => (st distanceTo start) < dist)
  }

  override def getTransferStations(station: StationScala, range: DistanceScala): List[StationScala] = genRawStations(station, range)

  override def getPathsForStation(station: StationScala, starttime: TimePointScala, maxdelta: TimeDeltaScala): List[StationWithRoute] = {
    val baseScheds = (0 to 86400 by 300).map(packed => {
      SchedulePointScala(
        packed,
        255.toByte,
        0,
        packed / 300,
        DatabaseIDScala(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
      )
    })
      .filter(sched => {
        val pdiff = sched.packedTime - starttime.packedTime
        pdiff > 0 && pdiff < maxdelta.seconds || pdiff < 0 && 86400 + pdiff < maxdelta.seconds
      })
    val tstart = baseScheds.minBy(_.nextValidTime(starttime))
    val routes = Seq(
      TransitPathScala("Test Agency", "Lat " + station.latitude + " PLUS", tstart.packedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPathScala("Test Agency", "Lat " + station.latitude + " MINUS", tstart.packedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.latitude.hashCode())),
      TransitPathScala("Test Agency", "Lng " + station.longitude + " PLUS", tstart.packedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.longitude.hashCode())),
      TransitPathScala("Test Agency", "Lng " + station.longitude + " MINUS", tstart.packedTime, 288, DatabaseIDScala(databaseName, "chain".hashCode * 31 + station.longitude.hashCode()))
    )

    routes
      .map(rt => StationWithRoute(station, rt, baseScheds.filter(_.nextValidTime(starttime) < starttime + maxdelta).toList))
      .toList
  }

  override def getArrivableStations(start: StationWithRoute, starttime: TimePointScala, maxDelta: TimeDeltaScala): List[StationWithRoute] = {
    val station = start.station
    val route = start.route
    val usableScheds = (0 to 86400 by 300)
      .map(packed => {
        SchedulePointScala(
          packed,
          255.toByte,
          0,
          packed / 300,
          DatabaseIDScala(databaseName, packed * 31 * 31 + station.latitude.hashCode() * 31 + station.longitude.hashCode())
        )
      })
      .filter(_.nextValidTime(starttime) <= starttime + maxDelta)

    if (route.route.contains("Lat") && route.route.contains("PLUS")) {
      usableScheds
        .zipWithIndex
        .takeWhile({ case (_, ind) => station.latitude % .02 + (ind + 1) * .02 < 38 })
        .map({ case (sched, ind) =>
          val lat = station.latitude % 0.02 + (ind + 1) * 0.02
          val lng = station.longitude
          val id = lat.hashCode() * 31 + lng.hashCode()
          val nstat = StationScala(s"Test Station $id", lat, lng, DatabaseIDScala(databaseName, id))
          StationWithRoute(nstat, route, List(sched))
        })
        .toList
    }
    else if (route.route.contains("Lat") && route.route.contains("MINUS")) {
      usableScheds
        .zipWithIndex
        .takeWhile({ case (_, ind) => station.latitude % .02 - (ind + 1) * .02 > 37 })
        .map({ case (sched, ind) =>
          val lat = station.latitude % 0.02 - (ind + 1) * 0.02
          val lng = station.longitude
          val id = lat.hashCode() * 31 + lng.hashCode()
          val nstat = StationScala(s"Test Station $id", lat, lng, DatabaseIDScala(databaseName, id))
          StationWithRoute(nstat, route, List(sched))
        })
        .toList
    }
    else if (route.route.contains("Lng") && route.route.contains("PLUS")) {
      usableScheds
        .zipWithIndex
        .takeWhile({ case (_, ind) => station.longitude % .02 + (ind + 1) * .02 < -121 })
        .map({ case (sched, ind) =>
          val lat = station.latitude
          val lng = station.longitude % 0.02 + (ind + 1) * 0.02
          val id = lat.hashCode() * 31 + lng.hashCode()
          val nstat = StationScala(s"Test Station $id", lat, lng, DatabaseIDScala(databaseName, id))
          StationWithRoute(nstat, route, List(sched))
        })
        .toList
    }
    else if (route.route.contains("Lng") && route.route.contains("MINUS")) {
      usableScheds
        .zipWithIndex
        .takeWhile({ case (_, ind) => station.longitude % .02 - (ind + 1) * .02 > -123 })
        .map({ case (sched, ind) =>
          val lat = station.latitude
          val lng = station.longitude % 0.02 - (ind + 1) * 0.02
          val id = lat.hashCode() * 31 + lng.hashCode()
          val nstat = StationScala(s"Test Station $id", lat, lng, DatabaseIDScala(databaseName, id))
          StationWithRoute(nstat, route, List(sched))
        })
        .toList
    }
    else List.empty

  }

  override def servesPoint(point: LocationPointScala): Boolean = point.latitude > 37 && point.latitude < 38 && point.longitude > -123 && point.longitude < -121

  override def servesArea(point: LocationPointScala, range: DistanceScala): Boolean = {
    val maxlat = point.latitude + LocationPointScala.latitudeRange(point, range / 2)
    val minlat = point.latitude - LocationPointScala.latitudeRange(point, range / 2)
    val maxlng = point.longitude + LocationPointScala.longitudeRange(point, range / 2)
    val minlng = point.longitude - LocationPointScala.longitudeRange(point, range / 2)
    maxlat < 38 && minlat > 37 && minlng > -123 && maxlng < -121
  }

  override def close(): Unit = {}

  override def isUp: Boolean = true

  override def loadData(data: Seq[StationWithRoute]): Boolean = false

  @inline
  private final def buildFakeStation(lat: Double, lng: Double): StationScala = {
    val id = lat.hashCode() * 31 + lng.hashCode()
    StationScala("Test Station " + id, lat, lng, DatabaseIDScala(databaseName, id))
  }

  override def databaseName: String = "TESTDB"
}
