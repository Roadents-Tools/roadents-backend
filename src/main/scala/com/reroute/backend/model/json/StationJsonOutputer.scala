package com.reroute.backend.model.json

import com.reroute.backend.model.location.Station

object StationJsonOutputer extends JsonOutputer[Station] {

  override def output(inputObject: Station): String = {
    s"""{
          "latitude" : ${inputObject.latitude},
          "longitude" : ${inputObject.longitude},
          "name" : "${inputObject.name}",
          "type": "station"
        }"""
  }
}
