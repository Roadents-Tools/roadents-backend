package com.reroute.backend.stations.postgresql

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePoint, TimeDelta}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.{ArrivableRequest, PathsRequest, TransferRequest}
import com.reroute.backend.utils.postgres.{PostgresConfig, ResultSetIterator}

import scala.collection.breakOut
import scala.util.{Failure, Success, Try}

/**
 * A station database backed by a PostGIS + Postgresql GTFS-formatted database.
 *
 * @param config the parameters for the connection to the database
 */
class PostgresGtfsDb(private val config: PostgresConfig) extends StationDatabase {

  override val databaseName: String = {
    if (config.dbname != "") config.dbname
    else config.dburl.replace("jdbc:postgresql://", "")
  }

  private val conOpt: Try[Connection] = Try {

    Class.forName("org.postgresql.Driver")

    val props = new Properties()
    props.setProperty("user", config.user)
    props.setProperty("password", config.pass)
    props.setProperty("ssl", "true")
    props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
    props.setProperty("sslcompression", "true")

    DriverManager.getConnection(config.dburl, props)
  }

  override def getStartingStations(start: InputLocation, dist: Distance, limit: Int): Seq[Station] = {
    val res = conOpt.flatMap(runStartQuery(_, start, dist, limit))
    res match {
      case Success(out) => out
      case Failure(e) =>
        e.printStackTrace()
        List.empty
    }
  }

  private def runStartQuery(con: Connection, start: InputLocation, dist: Distance,
                            limit: Int): Try[Seq[Station]] = Try {
    val meters = dist.in(DistUnits.METERS)
    val stm = con.createStatement()
    val sql =
      s"""SELECT agencyid || ';;' || id AS id, name, lon, lat
           FROM gtfs_stops
           WHERE ST_DWITHIN(ST_POINT(${start.longitude}, ${start.latitude})::geography, latlng, $meters)
           LIMIT $limit"""
    val rs = stm.executeQuery(sql)
    new ResultSetIterator(rs, extractStation).toStream
  }

  override def getTransferStations(request: Seq[TransferRequest]): Map[TransferRequest, Seq[Station]] = {
    val res = conOpt.flatMap(runTransferQuery(_, request))
    res match {
      case Success(out) => out
      case Failure(e) =>
        e.printStackTrace()
        Map.empty
    }
  }

  private def runTransferQuery(con: Connection,
                               requests: Seq[TransferRequest]): Try[Map[TransferRequest, Seq[Station]]] = Try {
    val totalLimits = requests.map(_.limit).sum
    val limit = if (totalLimits < 0) Int.MaxValue else totalLimits
    val begin =s"""SELECT agencyid || ';;' || id AS id, name, lon, lat FROM gtfs_stops WHERE """
    val end = " \nLIMIT " + limit
    val sql = requests
      .map(req => {
        val meters = req.distance.in(DistUnits.METERS)
        s"""ST_DWITHIN(ST_POINT(${req.station.longitude}, ${req.station.latitude})::geography, latlng, $meters)"""
      })
      .mkString(begin, " \nOR ", end)

    val stm = con.createStatement()
    val rs = stm.executeQuery(sql)
    val fullResultSet = new ResultSetIterator(rs, extractStation).toStream

    requests
      .map(req => req -> fullResultSet.filter(_.distanceTo(req.station) <= req.distance).take(req.limit))(breakOut)
  }

  private def extractStation(rs: ResultSet): Station = {
    val name = rs.getString("name")
    val id = DatabaseID(databaseName, rs.getString("id"))
    val lat = rs.getDouble("lat")
    val lon = rs.getDouble("lon")
    Station(name, lat, lon, id)
  }

  override def getPathsForStation(request: Seq[PathsRequest]): Map[PathsRequest, Seq[StationWithRoute]] = {
    val res = conOpt.flatMap(runPathsForStationQuery(_, request))
    res match {
      case Success(out) => out
      case Failure(e) =>
        e.printStackTrace()
        Map.empty
    }
  }

  private def runPathsForStationQuery(con: Connection,
                                      request: Seq[PathsRequest]): Try[Map[PathsRequest, Seq[StationWithRoute]]] = Try {
    val totalLimits = request.map(_.limit).sum
    val limit = if (totalLimits < 0) Int.MaxValue else totalLimits

    val service_level_schedule =
      s"""coalesce((
          select
          sunday + 2 * monday + 4 * tuesday + 8 * wednesday + 16 * thursday + 32 * friday + 64 * saturday
          from gtfs_calendars
          where gtfs_calendars.serviceid_agencyid = gtfs_trips.serviceid_agencyid
          and gtfs_calendars.serviceid_id = gtfs_trips.serviceid_id
          ), 0) as ndats"""

    val minStart = request.map(_.starttime.unixtime / 1000).min
    val maxEnd = request.map(req => (req.starttime + req.maxdelta).unixtime / 1000).min

    val service_level_exception_format =
      s"""coalesce((
          select
	        string_agg(extract('dow' from gtfs_calendar_dates.tdate)::int % 7 || ':' || gtfs_calendar_dates.exceptiontype, ';')
	        from gtfs_calendar_dates
          where gtfs_calendar_dates.serviceid_agencyid = gtfs_trips.serviceid_agencyid
          and gtfs_calendar_dates.serviceid_id = gtfs_trips.serviceid_id
	        and gtfs_calendar_dates.tdate between abstime($minStart)::date and abstime($maxEnd)::date + 1
	        ), '') as cdats"""

    val begin =
      s"""SELECT
         gtfs_stop_times.stop_agencyid || ';;' || gtfs_stop_times.stop_id as stationid,
         gtfs_stop_times.gid AS schedid,
         gtfs_stop_times.stopsequence AS ind,
         gtfs_stop_times.arrivaltime AS packedTime,
         gtfs_stop_times.departuretime - gtfs_stop_times.arrivaltime AS fuzz,
         $service_level_schedule,
         $service_level_exception_format,
         gtfs_agencies.name AS agname,
         coalesce(gtfs_routes.longname || ' ' || gtfs_routes.shortname, gtfs_routes.longname, gtfs_routes.shortname, ' ') AS routename,
         coalesce(gtfs_trips.tripshortname || ' ' || gtfs_trips.tripheadsign, gtfs_trips.tripshortname, gtfs_trips.tripheadsign, 'n/a') AS tripname,
         gtfs_trips.stop_time_count AS tripsize,
         gtfs_routes."type" as typeind,
         gtfs_trips.agencyid || ';;' || gtfs_trips.id AS tripid
         FROM gtfs_stop_times
         LEFT JOIN gtfs_trips ON (
         gtfs_stop_times.trip_agencyid = gtfs_trips.agencyid and gtfs_stop_times.trip_id = gtfs_trips.id
         )
         LEFT JOIN gtfs_routes ON (
         gtfs_trips.route_agencyid = gtfs_routes.agencyid AND gtfs_trips.route_id = gtfs_routes.id
         )
         LEFT JOIN gtfs_agencies ON (
         gtfs_stop_times.stop_agencyid = gtfs_agencies.id
         ) WHERE gtfs_stop_times.arrivaltime != -999 AND (
        """
    val end = ")\n LIMIT " + limit
    val sql = request
      .map(req => {
        val agencyAndId = req.station.id.id.split(";;")
        val agency = agencyAndId(0)
        val id = agencyAndId(1)
        val minTime = req.starttime.packedTime + 1
        val maxTime = minTime + req.maxdelta.seconds.toInt
        s""" (gtfs_stop_times.stop_agencyid = '$agency' and gtfs_stop_times.stop_id = '$id' and gtfs_stop_times.departureTime between $minTime and $maxTime)"""
      })
      .mkString(begin, " \nOR ", end)

    println(sql)

    val stm = con.createStatement()
    val rs = stm.executeQuery(sql)
    val fullResultSet = new ResultSetIterator(rs, extractPathsInformation).toStream
    request.map(
      req => req -> fullResultSet.filter(isValidPathInfo(req, _)).map(mapPathInfo(req, _)).take(req.limit)
    )(breakOut)
  }

  private def isValidPathInfo(req: PathsRequest,
                              data: (DatabaseID, TransitPath, SchedulePoint)): Boolean = {
    val (stid, _, sched) = data
    if (req.station.id != stid) false
    else if (!sched.departsWithin(req.starttime, req.maxdelta)) false
    else true
  }

  private def mapPathInfo(req: PathsRequest,
                          data: (DatabaseID, TransitPath, SchedulePoint)): StationWithRoute = {
    val (_, path, sched) = data
    StationWithRoute(req.station, path, Seq(sched))
  }

  private def extractPathsInformation(rs: ResultSet): (DatabaseID, TransitPath, SchedulePoint) = {
    val pathid = DatabaseID(databaseName, rs.getString("tripid"))
    val path = TransitPath(
      rs.getString("agname"),
      rs.getString("routename"),
      rs.getString("tripname"),
      rs.getInt("tripsize"),
      pathid,
      decodeTransitType(rs.getInt("typeind"))
    )

    val schedid = DatabaseID(databaseName, rs.getString("schedid"))
    val baseValids = rs.getInt("ndats")
    val calMods = rs.getString("cdats")
    val validdays = (baseValids + calMods.split(";").map(decodeCalendarModifier).sum).toByte
    val sched = SchedulePoint(
      rs.getInt("packedTime"),
      validdays,
      rs.getInt("fuzz"),
      rs.getInt("ind"),
      schedid
    )

    val stid = DatabaseID(databaseName, rs.getString("stationid"))
    (stid, path, sched)
  }

  private def decodeTransitType(typeind: Int): String = typeind match {
    case 1 | 2 => "train"
    case 3 => "bus"
    case 4 => "ferry"
    case _ => "misc"
  }

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val res = conOpt.flatMap(runArrivableStationQuery(_, request))
    res match {
      case Success(out) => out
      case Failure(e) =>
        e.printStackTrace()
        Map.empty
    }
  }

  private def runArrivableStationQuery(con: Connection,
                                       request: Seq[ArrivableRequest]): Try[Map[ArrivableRequest, Seq[StationWithRoute]]] = Try {
    val totalLimits = request.map(_.limit).sum
    val limit = if (totalLimits < 0) Int.MaxValue else totalLimits

    val service_level_schedule =
      s"""coalesce((
          select
          sunday + 2 * monday + 4 * tuesday + 8 * wednesday + 16 * thursday + 32 * friday + 64 * saturday
          from gtfs_calendars
          where gtfs_calendars.serviceid_agencyid = gtfs_trips.serviceid_agencyid
          and gtfs_calendars.serviceid_id = gtfs_trips.serviceid_id
          ), 0) as ndats"""

    val minStart = request.map(_.starttime.unixtime / 1000).min
    val maxEnd = request.map(req => (req.starttime + req.maxdelta).unixtime / 1000).min

    val service_level_exception_format =
      s"""coalesce((
          select
	        string_agg(extract('dow' from gtfs_calendar_dates.tdate)::int % 7 || ':' || gtfs_calendar_dates.exceptiontype, ';')
	        from gtfs_calendar_dates
          where gtfs_calendar_dates.serviceid_agencyid = gtfs_trips.serviceid_agencyid
          and gtfs_calendar_dates.serviceid_id = gtfs_trips.serviceid_id
	        and gtfs_calendar_dates.tdate between abstime($minStart)::date and abstime($maxEnd)::date + 1
	        ), '') as cdats"""

    val begin =
      s"""SELECT
         gtfs_stop_times.trip_agencyid || ';;' || gtfs_stop_times.trip_id AS tripid,
         gtfs_stop_times.stop_agencyid || ';;' || gtfs_stop_times.stop_id as stationid,
         gtfs_stops.lat,
         gtfs_stops.lon,
         gtfs_stops.name AS stationname,
         gtfs_stop_times.gid AS schedid,
         gtfs_stop_times.stopsequence AS ind,
         gtfs_stop_times.arrivaltime AS packedTime,
         gtfs_stop_times.departuretime - gtfs_stop_times.arrivaltime AS fuzz,
         $service_level_schedule,
         $service_level_exception_format
         FROM gtfs_stop_times
         LEFT JOIN gtfs_trips ON (
           gtfs_stop_times.trip_agencyid = gtfs_trips.agencyid and gtfs_stop_times.trip_id = gtfs_trips.id
         )
         LEFT JOIN gtfs_stops ON (
           gtfs_stops.agencyid = gtfs_stop_times.stop_agencyid AND gtfs_stops.id = gtfs_stop_times.stop_id
         ) WHERE gtfs_stop_times.arrivaltime != -999 AND (
        """
    val end = ") LIMIT " + limit
    val sql = request
      .map(req => {
        val tripAgencyAndId = req.station.route.id.id.split(";;")
        val tripAgency = tripAgencyAndId(0)
        val tripId = tripAgencyAndId(1)
        val packedStart = req.starttime.packedTime + 1
        val packedEnd = req.starttime.packedTime + req.maxdelta.seconds.toInt
        val minInd = req.station.nextDepartureSched(req.starttime - TimeDelta(1000)).index
        s""" (gtfs_stop_times.trip_agencyid = '$tripAgency' and gtfs_stop_times.trip_id = '$tripId'
           and gtfs_stop_times.stopsequence > $minInd and gtfs_stop_times.departuretime between $packedStart and $packedEnd) """
      })
      .mkString(begin, " OR ", end)

    println(sql)

    val stm = con.createStatement()
    val rs = stm.executeQuery(sql)
    val fullResultSet = new ResultSetIterator(rs, extractArrivableInformation).toStream
    request
      .map(
        req => req -> fullResultSet.filter(isValidArrivable(req, _)).map(mapArrivable(req, _)).take(req.limit)
      )(breakOut)
  }

  private def extractArrivableInformation(rs: ResultSet): (DatabaseID, Station, SchedulePoint) = {
    val pathid = DatabaseID(databaseName, rs.getString("tripid"))

    val stationid = DatabaseID(databaseName, rs.getString("stationid"))
    val station = Station(
      rs.getString("stationname"),
      rs.getDouble("lat"),
      rs.getDouble("lon"),
      stationid
    )

    val schedid = DatabaseID(databaseName, rs.getString("schedid"))
    val baseValids = rs.getInt("ndats")
    val calMods = rs.getString("cdats")
    val validdays = (baseValids + calMods.split(";").map(decodeCalendarModifier).sum).toByte
    val sched = SchedulePoint(
      rs.getInt("packedTime"),
      validdays,
      rs.getInt("fuzz"),
      rs.getInt("ind"),
      schedid
    )

    (pathid, station, sched)
  }

  private def decodeCalendarModifier(mod: String): Int = {
    if (mod.isEmpty) return 0
    val dayAndType = mod.split(':').map(_.toInt)
    val day = dayAndType(0)
    val modType = dayAndType(1)
    val coeff = modType match {
      case 1 => 1
      case 2 => -1
    }
    coeff * (1 << day)
  }

  private def isValidArrivable(req: ArrivableRequest,
                               data: (DatabaseID, Station, SchedulePoint)): Boolean = {
    val (tripid, _, sched) = data
    if (req.station.route.id != tripid) false
    else if (!sched.arrivesWithin(req.starttime, req.maxdelta)) false
    else true
  }

  private def mapArrivable(req: ArrivableRequest,
                           data: (DatabaseID, Station, SchedulePoint)): StationWithRoute = {
    val (_, station, sched) = data
    StationWithRoute(station, req.station.route, Seq(sched))
  }

  override def servesPoint(point: LocationPoint): Boolean = config.area.forall({
    case (pt, dist) => pt.distanceTo(point) <= dist
  })

  override def servesArea(point: LocationPoint, range: Distance): Boolean = config.area.forall({
    case (pt, dist) => pt.distanceTo(point) + range <= dist
  })

  override def close(): Unit = conOpt.foreach(_.close())

  override def isUp: Boolean = conOpt.toOption.exists(con => !con.isClosed)
}
