package com.reroute.backend.model.json

import com.reroute.backend.model.location.StationScala

object StationJsonOutputer extends JsonOutputer[StationScala] {

  override def output(inputObject: StationScala): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "name" : "${inputObject.name}",
          "type": station
        }"""
  }
}
