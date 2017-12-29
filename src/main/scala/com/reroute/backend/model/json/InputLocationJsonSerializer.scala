package com.reroute.backend.model.json

import com.reroute.backend.model.location.InputLocation
import org.json.JSONObject

object InputLocationJsonSerializer extends JsonSerializer[InputLocation] {
  override def serialize(inputObject: InputLocation): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "type" : "input"
        }"""
  }

  override def deserialize(json: String): InputLocation = {
    val jsonObj = new JSONObject(json)
    InputLocation(jsonObj.getDouble("latitude"), jsonObj.getDouble("longitude"))
  }
}
