package com.reroute.backend.model.json

import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.location.{InputLocation, ReturnedLocation, Station}
import com.reroute.backend.model.routing._

object RouteStepJsonOutputer extends JsonOutputer[RouteStep] {
  override def output(inputObject: RouteStep): String = inputObject match {
    case stp: TransitStep => transitStepStringify(stp)
    case stp: WalkStep => walkStepStringify(stp)
    case _ => "null"
  }

  private def transitStepStringify(step: TransitStep): String = {
    s"""{
          "total_time": ${step.totaltime.seconds},
          "step_type": "transit",
          "start_point": ${StationJsonOutputer.output(step.startpt)},
          "end_point": ${StationJsonOutputer.output(step.endpt)},
          "wait_time": ${step.waittime.seconds.toInt},
          "travel_time": ${step.traveltime.seconds.toInt},
          "agency": "${step.transitpath.agency}",
          "route": "${step.transitpath.route}",
          "stops": ${step.stops},
          "transit_type": "${step.transitpath.transitType}"
        }"""
  }

  private def walkStepStringify(step: WalkStep): String = {
    val startJson = step.startpt match {
      case st: InputLocation => InputLocationJsonSerializer.serialize(st)
      case st: Station => StationJsonOutputer.output(st)
      case st: ReturnedLocation => ReturnedLocationJsonSerializer.serialize(st)
      case _ => "null"
    }
    val endJson = step.endpt match {
      case st: InputLocation => InputLocationJsonSerializer.serialize(st)
      case st: Station => StationJsonOutputer.output(st)
      case st: ReturnedLocation => ReturnedLocationJsonSerializer.serialize(st)
      case _ => "null"
    }
    s"""{
          "start_point" : $startJson,
          "end_point" : $endJson,
          "total_time" : ${step.totaltime.seconds.toInt},
          "walk_distance" : ${step.walkdistance.in(DistUnits.METERS)},
          "step_type" : "walk"
        }"""
  }

}
