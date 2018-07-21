package com.reroute.backend.stations.interfaces

import com.reroute.backend.model.location.StationWithRoute
import com.reroute.backend.stations.{ArrivableRequest, WalkableRequest}

trait StationDatabase {

  def getWalkableStations(request: Seq[WalkableRequest]): Map[WalkableRequest, Seq[StationWithRoute]]

  def getArrivableStations(request: Seq[ArrivableRequest]): Map[ArrivableRequest, Seq[StationWithRoute]]

  def databaseName: String

  def close(): Unit

  def isUp: Boolean
}