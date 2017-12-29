package com.reroute.backend.locations.helpers

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProvider
import com.reroute.backend.locations.test.TestLocationProvider
import com.reroute.backend.model.location.ReturnedLocation

import scala.collection.mutable

object LocationProviderManager {

  private var isTest = false
  private var loadedProviders: Seq[LocationProvider] = Nil

  private def initializeProvidersList(): Unit = {
    if (isTest) {
      loadedProviders = Seq()
    }
    else {
      loadedProviders = Seq(new TestLocationProvider())
    }
  }

  private def providers: Seq[LocationProvider] = {
    if (loadedProviders == Nil) {
      initializeProvidersList()
    }
    loadedProviders
  }

  def getLocations(request: Seq[LocationsRequest]): Map[LocationsRequest, Seq[ReturnedLocation]] = {
    var rval = mutable.Map[LocationsRequest, Seq[ReturnedLocation]]()
    providers
      .filter(_.isUp)
      .takeWhile(_ => request.exists(!rval.contains(_)))
      .map(prov => prov.queryLocations(request.filter(prov.validityFilter)))
      .foreach(rval.++=)
    rval.toMap
  }
}
