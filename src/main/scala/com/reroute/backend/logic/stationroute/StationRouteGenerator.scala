package com.reroute.backend.logic.stationroute

import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.location._
import com.reroute.backend.model.routing._
import com.reroute.backend.model.time.TimeDelta
import com.reroute.backend.stations.{ArrivableRequest, TransitDataRetriever}

import scala.collection.{breakOut, mutable}
import scala.util.{Success, Try}

object StationRouteGenerator extends LogicCore[StationRouteRequest] {

  def buildStationRouteList(req: StationRouteRequest): Seq[Route] = {
    val rval = mutable.LinkedHashMap[Int, Route]()
    val baseRoute = new Route(req.start, req.starttime)
    if (req.yieldFilter(baseRoute)) rval.put(locationTag(req.start), baseRoute)

    val seedRoutes = genStartRoutes(req).filter(req.branchFilter).take(req.branchLimit)
    println(s"Got ${seedRoutes.size} walk seeds.")
    val seedTransit = genTransitRoutes(seedRoutes, req)
    println(s"Got ${seedTransit.size} transit seeds.")
    seedTransit
      .filter(!_.currentEnd.overlaps(req.start))
      .filter(req.yieldFilter)
      .foreach(elem => rval.put(locationTag(elem.currentEnd), elem))
    var layer = seedTransit.filter(req.branchFilter).take(req.branchLimit)
    while (layer.nonEmpty) {
      println(s"LAYER OF SIZE ${layer.size}")
      val rawlayer = nextLayer(layer, req)
      println(s"RAWLAYER OF SIZE ${rawlayer.size}")
      rawlayer
        .filter(req.yieldFilter)
        .filter(rt => {
          rval.get(locationTag(rt.currentEnd)).forall(existing => existing.totalTime > rt.totalTime)
        })
        .foreach(rt => rval.put(locationTag(rt.currentEnd), rt))
      layer = rawlayer.filter(req.branchFilter).take(req.branchLimit)
      println(s"FILTEREDLAYER OF SIZE ${layer.size}")
    }
    rval.values.toStream.sortBy(_.steps.size).take(req.yieldLimit)
  }

  @inline
  private def genStartRoutes(req: StationRouteRequest): Seq[Route] = {
    val baseroute = new Route(req.start, req.starttime)
    getStartNodes(req).map(step => baseroute + step)
  }

  @inline
  private def getStartNodes(req: StationRouteRequest): Seq[StartWalkStep] = {
    val params = req.queryGenerator.genStartQuery(req.start)
    println(s"Start params: ${params._1}, ${params._2.in(DistUnits.KILOMETERS)}, ${params._3}")
    TransitDataRetriever.getStartingStations(params._1, params._2, params._3)
      .map(stat => {
        val walkMinutes = req.start.distanceTo(stat).in(DistUnits.AVG_WALK_MINUTES)
        val walkTime = walkMinutes * TimeDelta.MINUTE
        StartWalkStep(req.start, stat, walkTime)
      })
  }

  @inline
  private def nextLayer(curlayer: Seq[Route], req: StationRouteRequest) = {
    val walklayer = genTransferRoutes(curlayer, req).toStream
      .filter(req.branchFilter)
      .take(req.branchLimit)
    genTransitRoutes(walklayer, req).toStream
  }

  @inline
  private def genTransferRoutes(curlayer: Seq[Route], req: StationRouteRequest) = genTransferNodes(curlayer, req)
    .flatMap({ case (route, possiblesteps) => possiblesteps.map(step => route + step) })

  @inline
  private def genTransferNodes(curlayer: Seq[Route], req: StationRouteRequest) = {
    val layerSize = curlayer.size
    val reqsMap = curlayer.map(rt => rt -> req.queryGenerator.genTransferQuery(rt, layerSize)).toMap

    val res = TransitDataRetriever.getTransferStations(reqsMap.values.toSeq)
    val rval = curlayer
      .filter(rt => rt.currentEnd.isInstanceOf[Station])
      .map(rt => rt -> res(reqsMap(rt)).toStream.filter(!rt.hasPoint(_)).map(
        st => TransferWalkStep(rt.currentEnd.asInstanceOf[Station], st, rt.currentEnd.distanceTo(st).avgWalkTime)))
    rval
  }

  @inline
  private def locationTag(loc: LocationPoint): Int = {
    (loc.latitude.toString + ":" + loc.longitude.toString).hashCode
  }

  @inline
  private def genTransitRoutes(curlayer: Seq[Route], req: StationRouteRequest): Seq[Route] = {
    if (curlayer.isEmpty) return Seq.empty
    val layersize = curlayer.size
    val pathReqs = curlayer
      .withFilter(rt => rt.currentEnd.isInstanceOf[Station])
      .map(rt => rt -> req.queryGenerator.genPathsQuery(rt, layersize))
      .toMap

    println(s"Got pathreq head: ${pathReqs.headOption.map(_._2)}")
    val pathResults = TransitDataRetriever.getPathsForStation(pathReqs.values.toSeq)
    println(s"Got ${pathResults.values.map(_.size).sum} paths.")

    val arrivablereqs: Map[StationWithRoute, ArrivableRequest] = curlayer.flatMap(route => {
      if (!route.currentEnd.isInstanceOf[Station]) Seq.empty
      else pathResults(pathReqs(route)).map(
        arrDat => arrDat -> req.queryGenerator.genArrivableQuery(route, arrDat, layersize))
    })(breakOut)

    val arrivableres = TransitDataRetriever.getArrivableStation(arrivablereqs.values.toSeq)
    println(s"Got ${arrivableres.values.map(_.size).sum} arrivables.")

    val rval = for (rt <- curlayer; startStation <- pathResults(pathReqs(rt))) yield {
      val tstart = startStation.nextDeparture(rt.endTime)
      val waittime = rt.endTime timeUntil tstart
      if (waittime > TimeDelta.NULL) arrivableres(arrivablereqs(startStation))
        .filter(st => !rt.hasPoint(st.station))
        .map(FromDataTransitStep(startStation, _, tstart, waittime))
        .map(rt + _)
      else Seq.empty
    }
    rval.flatten
  }

  override val tag: String = "STATION_ROUTE"

  override def runLogic(request: StationRouteRequest): ApplicationResult = Try {
    ApplicationResult.Result(buildStationRouteList(request))
  } recoverWith {
    case e: Throwable => Success(ApplicationResult.Error(Seq(e.getMessage)))
  } getOrElse ApplicationResult.Error(Seq("An unknown error occurred."))

  override def isValid(request: StationRouteRequest): Boolean = {
    request.tag == this.tag
  }
}
