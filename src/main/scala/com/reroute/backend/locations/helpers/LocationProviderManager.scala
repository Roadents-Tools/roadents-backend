package com.reroute.backend.locations.helpers

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProvider
import com.reroute.backend.locations.osmpostgres.PostgresModifiedOsmDb
import com.reroute.backend.locations.test.TestLocationProvider
import com.reroute.backend.model.location.ReturnedLocation
import com.reroute.backend.utils.postgres.PostgresConfig

import scala.collection.mutable

object LocationProviderManager {

  private var isTest = false
  private var loadedProviders: Seq[LocationProvider] = Nil

  private val psqlConfigs = Seq(
    PostgresConfig(
      dbname = "localdb",
      dburl = "jdbc:postgresql://debstop.dynamic.ucsd.edu:5433/locations"
    )
  )

  private def initializeProvidersList(): Unit = {
    loadedProviders = if (isTest) Seq(new TestLocationProvider()) else {
      psqlConfigs.map(new PostgresModifiedOsmDb(_))
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
      .takeWhile(_ => request.exists(req => !rval.contains(req)))
      .map(prov => prov.queryLocations(request.filter(prov.validityFilter)))
      .foreach(rval.++=)
    rval.toMap
  }
}
