package com.reroute.backend.stations.postgresql

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.location._
import com.reroute.backend.model.time.{SchedulePoint, TimeDelta}
import com.reroute.backend.stations.interfaces.StationDatabase
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}
import com.reroute.backend.utils.postgres.{PostgresConfig, ResultSetIterator}
import com.typesafe.scalalogging.Logger

import scala.collection.breakOut
import scala.util.{Failure, Success, Try}

/**
 * A station database backed by a PostGIS + Postgresql GTFS-formatted database.
 *
 * @param config the parameters for the connection to the database
 */
class PostgresGtfsDb(private val config: PostgresConfig) extends StationDatabase {

  private final val logger = Logger[PostgresGtfsDb]

  override val databaseName: String = {
    if (config.dbname != "") config.dbname
    else config.dburl.replace("jdbc:postgresql://", "")
  }


  private def conOpt: Try[Connection] = con.filter(!_.isClosed).recoverWith({
    case e =>
      logger.error(e.getMessage)
      con = initConnection()
      if (con.isFailure) {
        logger.error(s"Error with database ${config.dbname}.")
        logger.error(s"Config: $config")
        logger.error(s"Message: ${con.failed.get.getMessage}")
        logger.error("", con.failed.get)
      }
      con
  })

  private def initConnection(): Try[Connection] = Try {

    Class.forName("org.postgresql.Driver")

    val props = new Properties()
    props.setProperty("user", config.user)
    props.setProperty("password", config.pass)
    if (config.ssl) {
      props.setProperty("ssl", "true")
      props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
      props.setProperty("sslcompression", "true")
    }

    DriverManager.getConnection(config.dburl, props)
  }

  private var con: Try[Connection] = Failure(new NoSuchElementException("Need to initialize connection."))

  override def close(): Unit = conOpt.foreach(_.close())

  override def isUp: Boolean = conOpt.toOption.exists(cn => !cn.isClosed)

  override def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]] = {
    val res = conOpt.flatMap(runArrivableStationQuery(_, request))
    res match {
      case Success(out) => out
      case Failure(e) =>
        logger.error(e.getMessage, e)
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
      .map(arrivableRequestClause)
      .mkString(begin, " OR ", end)

    val stm = con.createStatement()
    val rs = stm.executeQuery(sql)
    val fullResultSet = new ResultSetIterator(rs, extractArrivableInformation).toStream
    request
      .map(
        req => req -> fullResultSet.filter(isValidArrivable(req, _)).map(mapArrivable(req, _)).take(req.limit)
      )(breakOut)
  }

  private def arrivableRequestClause(req: ArrivableRequest): String = {
    val isNeg = req.maxdelta.unixdelta < 0
    val tripAgencyAndId = req.station.route.id.id.split(";;")
    val tripAgency = tripAgencyAndId(0)
    val tripId = tripAgencyAndId(1)
    val packedBase = req.starttime.packedTime + (if (isNeg) -1 else 1)
    val packedEdge = packedBase + req.maxdelta.seconds.toInt

    if (isNeg) {
      val edgeInd = req.station.prevArrivalSched(req.starttime + TimeDelta.SECOND).index
      s""" (gtfs_stop_times.trip_agencyid = '$tripAgency'
           and gtfs_stop_times.trip_id = '$tripId'
           and gtfs_stop_times.stopsequence < $edgeInd
           and gtfs_stop_times.departuretime between $packedEdge and $packedBase) """

    }
    else {
      val edgeInd = req.station.nextDepartureSched(req.starttime - TimeDelta.SECOND).index
      s""" (gtfs_stop_times.trip_agencyid = '$tripAgency'
           and gtfs_stop_times.trip_id = '$tripId'
           and gtfs_stop_times.stopsequence > $edgeInd
           and gtfs_stop_times.arrivaltime between $packedBase and $packedEdge) """
    }

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

  def runWalkableStationsQuery(con: Connection,
                               request: Seq[WalkableRequest]): Try[Map[WalkableRequest, Seq[StationWithRoute]]] = Try {

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
         gtfs_stops.lat,
         gtfs_stops.lon,
         gtfs_stops.name as stationname,
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
         LEFT JOIN gtfs_stops ON (
         gtfs_stop_times.stop_agencyid=gtfs_stops.agencyid AND gtfs_stop_times.stop_id=gtfs_stops.id
         )
         LEFT JOIN gtfs_agencies ON (
         gtfs_stop_times.stop_agencyid = gtfs_agencies.id
         ) WHERE gtfs_stop_times.arrivaltime != -999 AND (
        """
    val end = ") LIMIT " + limit
    val sql = request
      .map(walkableRequestClause)
      .mkString(begin, " OR ", end)

    val stm = con.createStatement()
    val rs = stm.executeQuery(sql)
    val fullResultSet = new ResultSetIterator(rs, extractStationInformation).toStream
    request
      .map(
        req => req -> fullResultSet.filter(isValidWalkable(req, _)).take(req.limit)
      )(breakOut)
  }

  private def extractStationInformation(rs: ResultSet): StationWithRoute = {

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

    val pathid = DatabaseID(databaseName, rs.getString("tripid"))
    val path = TransitPath(
      rs.getString("agname"),
      rs.getString("routename"),
      rs.getString("tripname"),
      rs.getInt("tripsize"),
      pathid,
      decodeTransitType(rs.getInt("typeind"))
    )

    StationWithRoute(station, path, Seq(sched))
  }

  private def decodeTransitType(typeind: Int): String = typeind match {
    case 1 | 2 => "train"
    case 3 => "bus"
    case 4 => "ferry"
    case _ => "misc"
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

  private def walkableRequestClause(req: WalkableRequest): String = {
    val refTime = req.starttime.packedTime + 1
    val rangeBorder = refTime + req.maxdelta.seconds.toInt
    val meters = req.maxdist.in(DistUnits.METERS)

    if (refTime < rangeBorder) {
      s"""(
        ST_DWITHIN(ST_POINT(${req.center.longitude}, ${req.center.latitude})::geography, latlng, $meters)
        AND gtfs_stop_times.departureTime between $refTime and $rangeBorder)"""
    }
    else {
      s""" (
        ST_DWITHIN(ST_POINT(${req.center.longitude}, ${req.center.latitude})::geography, latlng, $meters)
        gtfs_stop_times.arrivalTime between $rangeBorder and $refTime)"""
    }
  }

  private def isValidWalkable(req: WalkableRequest, route: StationWithRoute): Boolean = {
    req.center.distanceTo(route.station) <= req.maxdist && (
      (req.maxdelta < TimeDelta.NULL && route.arrivesWithin(req.starttime, req.maxdelta)) ||
        (req.maxdelta > TimeDelta.NULL && route.departsWithin(req.starttime, req.maxdelta))
      )
  }

  override def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]] = {
    val res = conOpt.flatMap(runWalkableStationsQuery(_, request))
    res match {
      case Success(out) => out
      case Failure(e) =>
        logger.error(e.getMessage, e)
        Map.empty
    }
  }
}
