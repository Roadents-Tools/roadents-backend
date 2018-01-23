package com.reroute.backend.model.json

import com.reroute.backend.model.routing.Route

object RouteJsonOutputer extends JsonOutputer[Route] {
  override def output(inputObject: Route): String = {
    val startJson = LocationJsonOutputer.output(inputObject.start)
    val endJson = LocationJsonOutputer.output(inputObject.currentEnd)
    s"""{
          "start_point" : $startJson,
          "end_point" : $endJson,
          "start_time" : ${inputObject.starttime.unixtime / 1000},
          "total_time" : ${inputObject.totalTime.seconds.toInt},
          "steps" : ${inputObject.steps.reverse.map(RouteStepJsonOutputer.output).mkString("[", ",", "]")}
        }"""
  }
}
