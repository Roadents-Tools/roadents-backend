package com.reroute.backend.locations.osmpostgres

import java.nio.file.{Files, Paths}
import java.sql.DriverManager
import java.util.Properties

import com.reroute.backend.utils.postgres.PostgresConfig
import net.sf.extjwnl.data.{POS, PointerUtils}
import net.sf.extjwnl.dictionary.Dictionary
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConverters._
import scala.util.Try

object PostgresModifiedOsmLoader {

  private final val notTags = Set(
    "height", "gnis:*",
    "phone", "website", "building:*",
    "addr:*", "fax", "capacity", "floor",
    "wikidata", "wheelchair", "name:*",
    "import_uuid", "wikipedia", "flags",
    "contact:*", "source_ref", "route_ref", "opening_hours*",
    "level*", "description", "attribution", "source*",
    "is_in", "id", "toilets", "diet", "contact:*",
    "layer", "email", "smoking"
  )
  private final val keyMappings: Map[String, Seq[String]] = Map(
    "cuisine" -> Seq("meal", "cuisine"),
    "diet" -> Seq("meal", "diet")
  )
  private final val vlMappings: Map[String, Seq[String]] = Map(
    "bbq" -> Seq("barbecue", "meat", "food", "meal", "bbq"),
    "restaurant" -> Seq("food", "restaurant", "meal"),
    "cafe" -> Seq("cafe", "food", "coffee"),
    "brewpub" -> Seq("alcohol", "food", "pub", "brewery", "brewpub")
  )
  private final val bulkSize = 1000
  private val dict = Dictionary.getDefaultResourceInstance

  def getInserts(source: String): Seq[String] = {
    getInsertValues(source)
      .grouped(bulkSize)
      .map(_.mkString("INSERT INTO locations VALUES ", ", \n", "\n\n"))
      .toSeq
  }

  private def getInsertValues(source: String): Seq[String] = PostgresModifiedOsmLoader.loadObjs(source)
    .par
    .map(convertJson)
    .map(createInsertValue)
    .seq

  private def loadObjs(sourceFile: String): Seq[JSONObject] = {
    Files.lines(Paths.get(sourceFile)).iterator().asScala
      .toStream
      .drop(5)
      .filter(_.lengthCompare(1) > 0)
      .map(ln => if (ln.endsWith(",")) ln.substring(0, ln.length - 1) else ln)
      .map(new JSONObject(_))
      .filter(json => json.getJSONObject("properties").has("name"))
      .filter(json => json.getJSONObject("properties").has("other_tags"))
  }

  private def convertJson(json: JSONObject): JSONObject = {
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
      .filter(isValidTag)
      .flatMap(keyMappings.get)
      .flatten
      .flatMap(getExtraKeyTags)
    val extraValTags = objTags.keys().asScala
      .map(_.asInstanceOf[String])
      .filter(isValidTag)
      .flatMap(ky => Seq(objTags.optString(ky)))
      .flatMap(vl => vlMappings.getOrElse(vl, Seq(vl)))
      .flatMap(getExtraValTags)
    val extraTags = (extraKeyTags ++ extraValTags).toSeq.distinct.foldLeft(new JSONArray())(_ put _)
    propsObj.put("other_tags", objTags)
    propsObj.put("extra_tags", extraTags)
    rval.put("properties", propsObj)
    rval
  }

  private def isValidTag(tag: String) = {
    !notTags.contains(tag) &&
      notTags
        .filter(_.endsWith("*"))
        .map(_.replace("*", ""))
        .forall(!tag.startsWith(_))
  }

  private def getExtraKeyTags(key: String): Seq[String] = getExtraTags(key)

  private def getExtraTags(base: String): Seq[String] = {
    val indWords = Stream(
      Option(dict.getIndexWord(POS.ADJECTIVE, base)),
      Option(dict.getIndexWord(POS.NOUN, base))
    ).flatten
    val syns = indWords.flatMap(_.getSenses.asScala)

    val baseFromSyns = syns
      .flatMap(_.getWords.asScala)
      .map(_.getLemma)

    val synonyms = syns
      .flatMap(syn => PointerUtils.getSynonyms(syn).asScala)
      .flatMap(_.getSynset.getWords.asScala)
      .map(_.getLemma)

    val holonyms = syns
      .flatMap(syn => PointerUtils.getHolonyms(syn).asScala)
      .flatMap(_.getSynset.getWords.asScala)
      .map(_.getLemma)

    val hypernyms = syns
      .flatMap(syn => PointerUtils.getDirectHypernyms(syn).asScala)
      .flatMap(_.getSynset.getWords.asScala)
      .map(_.getLemma)

    (baseFromSyns ++ synonyms ++ holonyms ++ hypernyms).distinct.filter(_.head.isLower)
  }

  private def getExtraValTags(vl: String): Seq[String] = getExtraTags(vl)

  private def createInsertValue(json: JSONObject): String = {
    s"""(
      ${json.getLong("id")},
      $$name_tag$$${json.getString("name")}$$name_tag$$,
      ST_POINT(${json.getDouble("longitude")}, ${json.getDouble("latitude")})::geography,
      $$json_tag$$${json.getJSONObject("properties").toString()}$$json_tag$$::jsonb
    )"""
      .replace("  ", "").replace("\n", "")
  }

  def runInserts(config: PostgresConfig, values: Seq[String]): Try[Int] = Try {

    val connection = {
      Class.forName("org.postgresql.Driver")
      val props = new Properties()
      props.setProperty("user", config.user)
      props.setProperty("password", config.pass)
      props.setProperty("ssl", "true")
      props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
      props.setProperty("sslcompression", "true")
      DriverManager.getConnection(config.dburl, props)
    }

    val stm = connection.createStatement()
    values
      .foreach(com => require(com.startsWith("INSERT") || com.startsWith("UPDATE"), s"Got malformed statement: $com"))
    values.foreach(stm.addBatch)
    val rval = stm.executeBatch()
    stm.execute("UPDATE locations SET searchvector= setweight(to_tsvector(name), 'A') || setweight(to_tsvector(properties), 'B')")
    stm.close()
    connection.close()
    rval.sum
  }
}
