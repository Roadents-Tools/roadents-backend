package com.reroute.backend.locations.osmpostgres

import java.nio.file.{Files, Paths}
import java.sql.DriverManager
import java.util.Properties

import com.reroute.backend.utils.postgres.PostgresConfig
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConverters._
import scala.util.Try

object PostgresModifiedOsmLoader {

  private final val valueMappings: Map[String, Seq[String]] = Map(
    "restaurant" -> Seq("food"),
    "cafe" -> Seq("coffee", "food"),
    "bar" -> Seq("pub", "alcohol", "drink"),
    "bbq" -> Seq("food", "meat"),
    "pub" -> Seq("bar", "alcohol", "drink"),
    "deli" -> Seq("food", "meat")
  )

  private final val keyMappings: Map[String, Seq[String]] = Map(
    "cuisine" -> Seq("food")
  )

  private final val bulkSize = 1000

  def loadObjs(sourceFile: String): Seq[JSONObject] = {
    Files.lines(Paths.get(sourceFile)).iterator().asScala
      .toStream
      .drop(5)
      .filter(_.lengthCompare(1) > 0)
      .map(ln => if (ln.endsWith(",")) ln.substring(0, ln.length - 1) else ln)
      .map(new JSONObject(_))
      .filter(json => json.getJSONObject("properties").has("name"))
      .filter(json => json.getJSONObject("properties").has("other_tags"))
  }

  def convertJson(json: JSONObject): JSONObject = {
    val rval = new JSONObject()
    rval.put("latitude", json.getJSONObject("geometry").getJSONArray("coordinates").getDouble(1))
    rval.put("longitude", json.getJSONObject("geometry").getJSONArray("coordinates").getDouble(0))
    rval.put("name", json.getJSONObject("properties").getString("name"))
    rval.put("id", json.getJSONObject("properties").getLong("osm_id"))
    val propsObj = json.getJSONObject("properties")
    propsObj.remove("name")
    propsObj.remove("osm_id")
    val objTags = propsObj.getString("other_tags").split("\",\"")
      .map(_.replace("\"", ""))
      .filter(!_.endsWith("=>"))
      .map(_.split("=>"))
      .map(pair => (pair(0), pair(1)))
      .foldLeft(new JSONObject())((json, pair) => json.put(pair._1, pair._2))
    val flags = objTags.keys().asScala
      .map(_.asInstanceOf[String])
      .filter(objTags.getString(_) == "yes")
      .toList
    val negFlags = objTags.keys().asScala
      .map(_.asInstanceOf[String])
      .filter(objTags.getString(_) == "no")
      .toList
    if (flags.nonEmpty) {
      flags.foreach(objTags.remove)
      objTags.put("flags", flags.asJavaCollection)
    }
    if (negFlags.nonEmpty) negFlags.foreach(objTags.remove)
    val extraKeyTags = objTags.keys().asScala
      .map(_.asInstanceOf[String])
      .flatMap(ky => keyMappings.getOrElse(ky, Seq.empty))
    val extraValTags = objTags.keys().asScala
      .map(_.asInstanceOf[String])
      .map(ky => objTags.get(ky).toString)
      .flatMap(vl => valueMappings.getOrElse(vl, Seq.empty))
    val extraTags = (extraKeyTags ++ extraValTags).toSeq.distinct.foldLeft(new JSONArray())(_ put _)
    propsObj.put("other_tags", objTags)
    propsObj.put("extra_tags", extraTags)
    rval.put("properties", propsObj)
    rval
  }

  def createInsertValue(json: JSONObject): String = {
    s"""(
      ${json.getLong("id")},
      $$name_tag$$${json.getString("name")}$$name_tag$$,
      ST_POINT(${json.getDouble("longitude")}, ${json.getDouble("latitude")})::geography,
      $$json_tag$$${json.getJSONObject("properties").toString()}$$json_tag$$::jsonb
    )"""
      .replace("  ", "").replace("\n", "")
  }

  def runInserts(config: PostgresConfig, values: Seq[String]): Try[Int] = Try {

    val conOpt = {
      Class.forName("org.postgresql.Driver")
      val props = new Properties()
      props.setProperty("user", config.user)
      props.setProperty("password", config.pass)
      props.setProperty("ssl", "true")
      props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
      props.setProperty("sslcompression", "true")
      DriverManager.getConnection(config.dburl, props)
    }

    val stm = conOpt.createStatement()
    values
      .grouped(bulkSize)
      .map(_.mkString("INSERT INTO locations VALUES ", ", \n", "\n\n"))
      .foreach(stm.addBatch)
    val rval = stm.executeBatch()
    stm.execute("UPDATE locations SET searchvector= setweight(to_tsvector(name), 'A') || setweight(to_tsvector(properties), 'B')")
    stm.close()
    conOpt.close()
    rval.sum
  }
}
