package com.reroute.backend.model.json

import com.reroute.backend.model.routing.Route

object RouteJsonOutputer extends JsonOutputer[Route] {
  override def output(inputObject: Route): String = {
    s"""{
          "start_pt" : ${InputLocationJsonSerializer.output(inputObject.start)},
          "end_pt" : ${inputObject.dest.map(ReturnedLocationJsonSerializer.output).getOrElse("null")},
          "start_time" : ${inputObject.starttime.unixtime / 1000},
          "total_time" : ${inputObject.totalTime.seconds},
          "steps" : ${inputObject.steps.map(RouteStepJsonOutputer.output).mkString("[", ",", "]")}
        }"""
  }
}
