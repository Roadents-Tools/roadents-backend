package com.reroute.backend.locations.helpers

import com.reroute.backend.locations.LocationsRequest
import com.reroute.backend.locations.interfaces.LocationProvider
import com.reroute.backend.locations.osmpostgres.PostgresModifiedOsmDb
import com.reroute.backend.locations.test.TestLocationProvider
import com.reroute.backend.model.location.ReturnedLocation
import com.reroute.backend.utils.postgres.PostgresConfig

import scala.collection.mutable

object LocationProviderManager {

  private var loadedProviders: Seq[LocationProvider] = Nil

  private def initializeDatabases(test: Boolean = false): Seq[LocationProvider] = {
    if (!test) {
      Seq(new PostgresModifiedOsmDb(PostgresConfig(
        dbname = sys.env("LOCDB_NAME"),
        dburl = sys.env("LOCDB_URL")
      )))
    }
    else {
      Seq(new TestLocationProvider())
    }
  }

  private def providers: Seq[LocationProvider] = {
    if (loadedProviders == Nil) {
      loadedProviders = initializeDatabases()
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

  def setTest(test: Boolean): Unit = {
    loadedProviders = initializeDatabases(test)
  }
}
