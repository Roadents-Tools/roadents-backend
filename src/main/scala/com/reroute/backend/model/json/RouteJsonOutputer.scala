package com.reroute.backend.model.json

import com.reroute.backend.model.routing.RouteScala

object RouteJsonOutputer extends JsonOutputer[RouteScala] {
  override def output(inputObject: RouteScala): String = {
    s"""{
          "start_pt" : ${StartJsonSerializer.output(inputObject.start)},
          "end_pt" : ${inputObject.dest.map(DestinationJsonSerializer.output).getOrElse("null")},
          "start_time" : ${inputObject.starttime.unixtime / 1000},
          "total_time" : ${inputObject.totalTime.seconds},
          "steps" : ${inputObject.steps.map(RouteStepJsonOutputer.output).mkString("[", ",", "]")}
        }"""
  }
}
