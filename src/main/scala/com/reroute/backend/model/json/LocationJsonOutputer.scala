package com.reroute.backend.model.json

import com.reroute.backend.model.location._
import org.json.JSONObject

object LocationJsonOutputer extends JsonOutputer[LocationPoint] {
  override def output(inputObject: LocationPoint): String = inputObject match {
    case inp: InputLocation => InputLocationJsonSerializer.output(inp)
    case ret: ReturnedLocation => ReturnedLocationJsonSerializer.output(ret)
    case stat: Station => StationJsonOutputer.output(stat)
    case _ => "null"
  }
}

object InputLocationJsonSerializer extends JsonSerializer[InputLocation] {
  override def serialize(inputObject: InputLocation): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "location_type" : "input"
        }"""
  }

  override def deserialize(json: String): InputLocation = {
    val jsonObj = new JSONObject(json)
    InputLocation(jsonObj.getDouble("latitude"), jsonObj.getDouble("longitude"))
  }
}

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

object StationJsonOutputer extends JsonOutputer[Station] {

  override def output(inputObject: Station): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "name" : "${inputObject.name}",
          "location_type": "station"
        }"""
  }
}
