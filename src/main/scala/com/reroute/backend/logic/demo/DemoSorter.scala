package com.reroute.backend.logic.demo

import com.reroute.backend.model.routing.Route

sealed case class DemoSorter[B](tag: String, sorter: Ordering[Route])

object DemoSorter {
  val VALUES = Array(
    DemoSorter("time MIN", Ordering.by((rt: Route) => rt.totalTime)),
    DemoSorter("time MAX", Ordering.by((rt: Route) => rt.totalTime).reverse),
    DemoSorter("node MIN", Ordering.by((rt: Route) => rt.steps.size)),
    DemoSorter("node MAX", Ordering.by((rt: Route) => rt.steps.size).reverse),
    DemoSorter("dist MIN", Ordering.by((rt: Route) => rt.distance)),
    DemoSorter("dist MAX", Ordering.by((rt: Route) => rt.distance).reverse),
    DemoSorter("disp MIN", Ordering.by((rt: Route) => rt.totalDistance)),
    DemoSorter("disp MAX", Ordering.by((rt: Route) => rt.totalDistance).reverse),
    DemoSorter("labor MIN", Ordering.by((rt: Route) => rt.walkDistance)),
    DemoSorter("labor MAX", Ordering.by((rt: Route) => rt.walkDistance).reverse),
    )
}
