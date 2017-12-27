package com.reroute.backend.model.json

import com.reroute.backend.model.location.StartScala
import org.json.JSONObject

object StartJsonSerializer extends JsonSerializer[StartScala] {
  override def serialize(inputObject: StartScala): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "type" : "input"
        }"""
  }

  override def deserialize(json: String): StartScala = {
    val jsonObj = new JSONObject(json)
    StartScala(jsonObj.getDouble("latitude"), jsonObj.getDouble("longitude"))
  }
}
