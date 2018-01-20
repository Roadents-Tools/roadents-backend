package com.reroute.backend.model.json

import com.reroute.backend.model.location.{DestCategory, ReturnedLocation}
import org.json.JSONObject

object ReturnedLocationJsonSerializer extends JsonSerializer[ReturnedLocation] {
  override def serialize(inputObject: ReturnedLocation): String =
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "name" : "${inputObject.name}",
          "location_type" : "returned",
          "tags" : ${inputObject.types.map("\"" + _.category + "\"").take(20).mkString("[", ",", "]")}
        }"""

  override def deserialize(jsonstr: String): ReturnedLocation = {
    val json = new JSONObject(jsonstr)
    val categoriesJson = json.getJSONArray("tags")
    val arrSize = categoriesJson.length()
    val categories = for (i <- 0 until arrSize) yield DestCategory(categoriesJson.getString(i))

    ReturnedLocation(
      json.getString("name"),
      json.getDouble("latitude"),
      json.getDouble("longitude"),
      categories
    )
  }
}
