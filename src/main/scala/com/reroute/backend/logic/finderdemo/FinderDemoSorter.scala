package com.reroute.backend.logic.finderdemo

import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.routing.Route

sealed case class FinderDemoSorter(tag: String, sorter: Ordering[(Route, Route)])

object FinderDemoSorter {
  val VALUES = Array(
    FinderDemoSorter("time MIN", makeSumOrdering(_.totalTime.minutes.toInt)),
    FinderDemoSorter("time MAX", makeSumOrdering(_.totalTime.minutes.toInt * -1)),

    FinderDemoSorter("node MIN", makeSumOrdering(_.steps.size)),
    FinderDemoSorter("node MIN", makeSumOrdering(_.steps.size * -1)),

    FinderDemoSorter("dist MIN", makeSumOrdering(_.distance.in(DistUnits.METERS).toInt)),
    FinderDemoSorter("dist MAX", makeSumOrdering(_.distance.in(DistUnits.METERS).toInt * -1)),

    FinderDemoSorter("disp MIN", makeSumOrdering(_.totalDistance.in(DistUnits.METERS).toInt)),
    FinderDemoSorter("disp MAX", makeSumOrdering(_.totalDistance.in(DistUnits.METERS).toInt * -1)),

    FinderDemoSorter("labor MIN", makeSumOrdering(_.walkDistance.in(DistUnits.METERS).toInt)),
    FinderDemoSorter("labor MIN", makeSumOrdering(_.walkDistance.in(DistUnits.METERS).toInt * -1)),
    )

  private def makeSumOrdering(f: Route => Int): Ordering[(Route, Route)] = {
    Ordering.by((rts: (Route, Route)) => rts match {
      case (rt1, rt2) => (f(rt1) + f(rt2), (rt1.totalTime - rt2.totalTime).abs)
    })(Ordering.Tuple2)
  }
}

