package com.reroute.backend.model.json

import com.reroute.backend.model.location.{DestCategory, DestinationScala}
import org.json.JSONObject

object DestinationJsonSerializer extends JsonSerializer[DestinationScala] {
  override def serialize(inputObject: DestinationScala): String =
    s"""{
       | "latitude" : ${inputObject.latitude},
       | "longitude" : ${inputObject.longitude},
       | "name" : ${inputObject.name},
       | "categories" : [${inputObject.types.map("\"" + _.category + "\"").mkString(",")}]
       |}""".stripMargin

  override def deserialize(jsonstr: String): DestinationScala = {
    val json = new JSONObject(jsonstr)
    val categoriesJson = json.getJSONArray("categories")
    val arrSize = categoriesJson.length()
    val categories = for (i <- 0 until arrSize) yield DestCategory(categoriesJson.getString(i))

    DestinationScala(
      json.getString("name"),
      json.getDouble("latitude"),
      json.getDouble("longitude"),
      categories
    )
  }
}
