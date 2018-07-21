package com.reroute.backend.logic.finderdemo

import com.reroute.backend.locations.{LocationRetriever, LocationsRequest}
import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.stationroute.{StationRouteGenerator, StationRouteRequest}
import com.reroute.backend.model.distance.{CircleArea, DistUnits, Distance}
import com.reroute.backend.model.location.ReturnedLocation
import com.reroute.backend.model.routing.{GeneralWalkStep, Route}
import com.reroute.backend.model.time.TimeDelta
import com.typesafe.scalalogging.Logger

object FinderDemoCore extends LogicCore[FinderDemoRequest] {
  override val tag: String = "FINDER_DEMO"

  private final val logger = Logger[FinderDemoCore.type]

  private final val RESULTS_PER_SORT = 15
  private final val DEST_LIMIT = 200

  override def runLogic(req: FinderDemoRequest): ApplicationResult = {

    //Get the raw station routes around starta and startb
    val seeds = findBaseAreas1(req)
    val usables = findBaseAreas2(req, seeds)
    logger.info(s"Got ${seeds.length} seeds and ${usables.length} usables.")

    //Find the usable overlaps
    val circleMaker = findOverlap(req)(_, _)
    val rawOverlaps = for (rta <- seeds; rtb <- usables) yield circleMaker(rta, rtb)
    val usableOverlaps = rawOverlaps.filter(_._3.range > TimeDelta.AVG_WALK_SPEED_PER_MINUTE * 2)
    logger.info(s"Got ${usableOverlaps.length} usable overlaps.")

    //Get the usable locations
    val locreqs = usableOverlaps
      .map(_._3)
      .map(c => LocationsRequest(c.center, c.range.in(DistUnits.AVG_WALK_MINUTES) * TimeDelta.MINUTE, req.query))
    val allLocs = LocationRetriever.getLocations(locreqs)
    logger.info(s"Got ${allLocs.size} total locations.")

    val combos = allLocs.values.flatten.toSet
      .take(DEST_LIMIT)
      .map((loc: ReturnedLocation) => {
        val (mina, minb, _) = usableOverlaps.minBy { case (rta, rtb, _) =>
          rta.totalTime + rtb.totalTime +
            rta.currentEnd.distanceTo(loc).avgWalkTime +
            rtb.currentEnd.distanceTo(loc).avgWalkTime
        }
        (attachDestination(mina, loc), attachDestination(minb, loc))
      })
      .toSeq
    logger.info(s"Got ${combos.length} combos.")

    //Create a mapping from each sorter to its returned starta-startb route pairs, and then flatten and return
    val empty = (new Route(req.starta, req.starttime), new Route(req.startb, req.starttime))
    val combosToSorts = FinderDemoSorter.VALUES
      .map(sorter => {
        val sorted = combos.sorted(sorter.sorter)
        sorter -> sorted.take(RESULTS_PER_SORT).padTo(RESULTS_PER_SORT, empty)
      })

    logger.info("Double checking.")

    for ((sort, rts) <- combosToSorts) {
      assert(rts.lengthCompare(RESULTS_PER_SORT) == 0)
      assert(
        rts.head._1.totalTime <= req.maxdelta && rts.head._2.totalTime <= req.maxdelta,
        s"Diffs: (${req.maxdelta.minutes - rts.head._1.totalTime.minutes}, ${req.maxdelta.minutes - rts.head._2.totalTime.minutes})"
      )
      for (idx <- 1 until RESULTS_PER_SORT) {
        assert(sort.sorter.lteq(rts(idx - 1), rts(idx)), s"Sorter ordering: ${sort.tag}")
        assert(rts(idx)._1.totalTime <= req.maxdelta && rts(idx)._2.totalTime <= req.maxdelta)
      }
    }

    logger.info("Returning...")

    val rval = combosToSorts
      .flatMap({ case (_, sorted) => sorted })
      .flatMap({ case (rta, rtb) => Seq(rta, rtb) })


    ApplicationResult.Result(rval)
  }

  private def findBaseAreas1(req: FinderDemoRequest): Seq[Route] = {
    val statratreq = StationRouteRequest
      .maxDeltaOnly(req.starta, req.starttime, req.maxdelta, 4 * DEST_LIMIT)
      .copy(
        branchFilter = primaryBranchFilter(req),
        yieldFilter = primaryYieldFilter(req)
      )
    StationRouteGenerator.buildStationRouteList(statratreq)
  }


  private def findBaseAreas2(req: FinderDemoRequest, toOverlap: Seq[Route]): Seq[Route] = {
    val statratreq = StationRouteRequest
      .maxDeltaOnly(req.starta, req.starttime, req.maxdelta, DEST_LIMIT)
      .copy(
        branchFilter = secondaryBranchFilter(req, toOverlap),
        yieldFilter = secondaryYieldFilter(req, toOverlap)
      )
    StationRouteGenerator.buildStationRouteList(statratreq)
  }

  private def primaryBranchFilter(req: FinderDemoRequest)(rt: Route): Boolean = {
    //time check
    if (rt.totalTime >= req.maxdelta) return false

    //dist checks
    val adist = req.starta.distanceTo(rt.currentEnd).in(DistUnits.METERS)
    val bdist = req.startb.distanceTo(rt.currentEnd).in(DistUnits.METERS)

    val usablemins = req.maxdelta.minutes

    val aworks = adist / TimeDelta.MAX_SPEED_PER_MINUTE.in(DistUnits.METERS) < usablemins
    val bworks = bdist / TimeDelta.MAX_SPEED_PER_MINUTE.in(DistUnits.METERS) < usablemins
    if (!aworks || !bworks) return false

    true
  }

  private def primaryYieldFilter(req: FinderDemoRequest)(rt: Route): Boolean = {
    //time check
    if (rt.totalTime >= req.maxdelta) return false

    //dist checks
    val adist = req.starta.distanceTo(rt.currentEnd).in(DistUnits.METERS)
    val bdist = req.startb.distanceTo(rt.currentEnd).in(DistUnits.METERS)

    val usablemins = req.maxdelta.minutes

    val aworks = adist / TimeDelta.MAX_SPEED_PER_MINUTE.in(DistUnits.METERS) < usablemins
    val bworks = bdist / TimeDelta.MAX_SPEED_PER_MINUTE.in(DistUnits.METERS) < usablemins
    if (!aworks || !bworks) return false

    //angle check
    //val abdist = req.startb.distanceTo(req.startb).in(DistUnits.METERS)
    //val angleworks = (adist * adist - bdist * bdist).abs <= abdist * abdist
    //if(!angleworks) return false

    true
  }

  private def secondaryBranchFilter(req: FinderDemoRequest, primary: Seq[Route]): Route => Boolean = {
    val reachableAreas = primary.map(prt => CircleArea(prt.currentEnd, (req.maxdelta - prt.totalTime).avgWalkDist))

    rt => {
      val area = CircleArea(rt.currentEnd, (req.maxdelta - rt.totalTime).minutes * TimeDelta.MAX_SPEED_PER_MINUTE)
      rt.totalTime < req.maxdelta && reachableAreas.exists(area.intersects)
    }

  }

  private def secondaryYieldFilter(req: FinderDemoRequest, primary: Seq[Route]): Route => Boolean = {
    val reachableAreas = primary.map(prt => CircleArea(prt.currentEnd, (req.maxdelta - prt.totalTime).avgWalkDist))

    rt => {
      val area = CircleArea(rt.currentEnd, (req.maxdelta - rt.totalTime).avgWalkDist)
      rt.totalTime < req.maxdelta && reachableAreas.exists(area.intersects)
    }
  }

  private def findOverlap(req: FinderDemoRequest)(rta: Route, rtb: Route): (Route, Route, CircleArea) = {
    val timeLeftA = req.maxdelta - rta.totalTime
    val radiusA = timeLeftA.minutes * TimeDelta.AVG_WALK_SPEED_PER_MINUTE
    val circleA = CircleArea(rta.currentEnd, radiusA)

    val timeLeftB = req.maxdelta - rtb.totalTime
    val radiusB = timeLeftB.minutes * TimeDelta.AVG_WALK_SPEED_PER_MINUTE
    val circleB = CircleArea(rtb.currentEnd, radiusB)

    if (!circleA.intersects(circleB)) (rta, rtb, circleA.copy(range = Distance.NULL))
    else (rta, rtb, circleA.intersection(circleB))
  }

  private def attachDestination(rt: Route, loc: ReturnedLocation): Route = {
    val walkTime = rt.currentEnd.distanceTo(loc).avgWalkTime
    val nstep = GeneralWalkStep(rt.currentEnd, loc, walkTime)
    rt + nstep
  }
}
