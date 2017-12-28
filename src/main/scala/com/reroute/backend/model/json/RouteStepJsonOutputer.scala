package com.reroute.backend.model.json

import com.reroute.backend.model.distance.DistanceUnitsScala
import com.reroute.backend.model.location.{DestinationScala, StartScala, StationScala}
import com.reroute.backend.model.routing._

object RouteStepJsonOutputer extends JsonOutputer[RouteStepScala] {
  override def output(inputObject: RouteStepScala): String = inputObject match {
    case stp: TransitStepScala => transitStepStringify(stp)
    case stp: WalkStepScala => walkStepStringify(stp)
    case _ => "null"
  }

  private def transitStepStringify(step: TransitStepScala): String = {
    s"""{
          "total_time": ${step.totaltime.seconds},
          "step_type": "transit",
          "start_pt": ${StationJsonOutputer.output(step.startpt)},
          "end_pt": ${StationJsonOutputer.output(step.endpt)},
          "wait_time": ${step.waittime.seconds},
          "travel_time": ${step.traveltime.seconds},
          "agency": "${step.transitpath.agency}",
          "route": "${step.transitpath.route}",
          "stops": ${step.stops},
          "transit_type": "${step.transitpath.transitType}"
        }"""
  }

  private def walkStepStringify(step: WalkStepScala): String = {
    val startJson = step.startpt match {
      case st: StartScala => StartJsonSerializer.serialize(st)
      case st: StationScala => StationJsonOutputer.output(st)
      case st: DestinationScala => DestinationJsonSerializer.serialize(st)
      case _ => "null"
    }
    val endJson = step.endpt match {
      case st: StartScala => StartJsonSerializer.serialize(st)
      case st: StationScala => StationJsonOutputer.output(st)
      case st: DestinationScala => DestinationJsonSerializer.serialize(st)
      case _ => "null"
    }
    s"""{
          "start_pt" : $startJson,
          "end_pt" : $endJson,
          "total_time" : ${step.totaltime.seconds.round},
          "walk_distance" : ${step.walkdistance.in(DistanceUnitsScala.METERS)},
          "step_type" : "walk"
        }"""
  }

}
