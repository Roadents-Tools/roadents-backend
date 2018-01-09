package com.reroute.backend.locations.osmpostgres

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProvider
import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.location.{DestCategory, ReturnedLocation}
import com.reroute.backend.utils.postgres.{PostgresConfig, ResultSetIterator}
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.util.{Failure, Random, Success, Try}

class PostgresModifiedOsmDb(val config: PostgresConfig) extends LocationProvider {


  private final val notTags = Set(
    "height", "gnis:*",
    "phone", "website", "building:*",
    "addr:*", "fax", "capacity", "floor",
    "wikidata", "wheelchair", "name:*",
    "import_uuid", "wikipedia", "flags",
    "contact:*", "source_ref", "route_ref", "opening_hours*",
    "level*", "description", "attribution", "source*",
    "is_in:*", "id", "toilets", "diet", "contact:*",
    "layer", "email", "smoking"
  )

  private var con: Try[Connection] = Failure(new NoSuchElementException("Need to initialize connection."))

  private def initConnection(): Try[Connection] = Try {

    Class.forName("org.postgresql.Driver")

    val props = new Properties()
    props.setProperty("user", config.user)
    props.setProperty("password", config.pass)
    props.setProperty("ssl", "true")
    props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
    props.setProperty("sslcompression", "true")

    DriverManager.getConnection(config.dburl, props)
  }

  private def conOpt: Try[Connection] = con.filter(!_.isClosed).recoverWith({
    case e =>
      println(e.getMessage)
      con = initConnection()
      if (con.isFailure) println("New connection err:" + con.failed.get.getMessage)
      con
  })

  private val rng = new Random()

  private def convertQueryClause(query: DestCategory) = {
    val tag = rng.alphanumeric.filterNot(_.isDigit).take(7).mkString
    s" plainto_tsquery($$$tag$$${query.category}$$$tag$$) @@ locations.searchvector "
  }

  private def validTag(tg: String) = {
    !notTags.contains(tg) &&
      !notTags.filter(_.endsWith("*"))
        .map(_.replace("*", ""))
        .exists(tg.startsWith)
  }

  private def convertRequestClause(req: LocationsRequest, addSearch: Boolean): String = {
    val meters = req.timeRange.avgWalkDist.in(DistUnits.METERS)
    val longitude = req.center.longitude
    val latitude = req.center.latitude
    val query = req.searchquery
    if (addSearch) {
      s"""(ST_DWITHIN(latlng, ST_POINT($longitude, $latitude)::geography, $meters) AND ${convertQueryClause(query)})"""
    }
    else {
      s"""(ST_DWITHIN(latlng, ST_POINT($longitude, $latitude)::geography, $meters))"""
    }
  }

  private def rsMapper(rs: ResultSet): ReturnedLocation = {
    val name = rs.getString("name")
    val lat = rs.getDouble("lat")
    val lng = rs.getDouble("lng")

    val rawPropsStr = rs.getString("rawjson")
    val rawProps = new JSONObject(rawPropsStr)

    val extrasJson = rawProps.getJSONArray("extra_tags")
    val extrasLen = extrasJson.length()
    val extras = (0 until extrasLen).map(extrasJson.getString)

    val othersJson = rawProps.getJSONObject("other_tags")

    val flagsArr = Option(othersJson.optJSONArray("flags")).getOrElse(new JSONArray())
    val flagsLen = flagsArr.length()
    val flags = (0 until flagsLen).map(flagsArr.getString)

    othersJson.remove("flags")
    val otherkeys = othersJson.keys().asScala
      .map(_.asInstanceOf[String])
      .filter(validTag)
      .map(othersJson.getString)

    val keyWordsField = extras ++ flags ++ otherkeys

    ReturnedLocation(name, lat, lng, keyWordsField.map(DestCategory))
  }

  private def meetsQuery(dest: ReturnedLocation, req: LocationsRequest): Boolean = {
    dest.distanceTo(req.center) <= req.timeRange.avgWalkDist
  }

  override def queryLocations(requests: Seq[LocationsRequest]): Map[LocationsRequest, Seq[ReturnedLocation]] = {

    //TODO: Figure out how to query flags to test if each location matches each request's individual category
    require(requests.map(_.searchquery).distinct.lengthCompare(1) == 0, s"Got too many categories!")

    val addIndSearch = requests.toStream.map(_.searchquery).distinct.lengthCompare(1) > 0
    val queryHead = if (addIndSearch) {
      """SELECT id, name, ST_X(latlng::geometry) AS lng, ST_Y(latlng::geometry) AS lat, properties::text AS rawjson
        FROM locations WHERE """
    }
    else {
      s"""SELECT id, name, ST_X(latlng::geometry) AS lng, ST_Y(latlng::geometry) AS lat, properties::text AS rawjson
         FROM locations WHERE ${convertQueryClause(requests.head.searchquery)} AND """
    }
    val query = requests
      .map(convertRequestClause(_, addIndSearch))
      .mkString(queryHead, " OR ", "\n\n")

    val con = conOpt match {
      case Success(cn) => cn
      case Failure(_) => return Map.empty
    }

    val stm = con.createStatement()
    val rsRaw = stm.executeQuery(query)
    val results = new ResultSetIterator(rsRaw, rsMapper).toStream
    requests
      .map(req => req -> results.filter(meetsQuery(_, req)))(breakOut)
  }

  override def validityFilter(req: LocationsRequest): Boolean = true

  override def isUp: Boolean = conOpt.map(!_.isClosed).getOrElse(false)

  override def close(): Unit = conOpt.foreach(_.close())
}
