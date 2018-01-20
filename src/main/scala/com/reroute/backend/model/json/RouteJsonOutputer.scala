package com.reroute.backend.model.json

import com.reroute.backend.model.routing.Route

object RouteJsonOutputer extends JsonOutputer[Route] {
  override def output(inputObject: Route): String = {
    s"""{
          "start_point" : ${InputLocationJsonSerializer.output(inputObject.start)},
          "end_point" : ${inputObject.dest.map(ReturnedLocationJsonSerializer.output).getOrElse("null")},
          "start_time" : ${inputObject.starttime.unixtime / 1000},
          "total_time" : ${inputObject.totalTime.seconds.toInt},
          "steps" : ${inputObject.steps.reverse.map(RouteStepJsonOutputer.output).mkString("[", ",", "]")}
        }"""
  }
}
