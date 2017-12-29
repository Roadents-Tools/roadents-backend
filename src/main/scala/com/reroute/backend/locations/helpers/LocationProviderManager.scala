package com.reroute.backend.locations.helpers

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProviderScala
import com.reroute.backend.locations.test.TestLocationProviderScala
import com.reroute.backend.model.location.DestinationScala

import scala.collection.mutable

object LocationProviderManager {

  private var isTest = false
  private var loadedProviders: Seq[LocationProviderScala] = Nil

  private def initializeProvidersList(): Unit = {
    if (isTest) {
      loadedProviders = Seq()
    }
    else {
      loadedProviders = Seq(new TestLocationProviderScala())
    }
  }

  private def providers: Seq[LocationProviderScala] = {
    if (loadedProviders == Nil) {
      initializeProvidersList()
    }
    loadedProviders
  }

  def getLocations(request: Seq[LocationsRequest]): Map[LocationsRequest, Seq[DestinationScala]] = {
    var rval = mutable.Map[LocationsRequest, Seq[DestinationScala]]()
    providers
      .filter(_.isUp())
      .takeWhile(_ => request.exists(!rval.contains(_)))
      .map(prov => prov.queryLocations(request.filter(prov.validityFilter)))
      .foreach(rval.++=)
    rval.toMap
  }
}
