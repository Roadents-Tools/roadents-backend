package com.reroute.backend.model.location

import com.reroute.backend.model.database.{DatabaseIDScala, DatabaseObjectScala}

case class StationScala(
                         name: String,
                         override val latitude: Double,
                         override val longitude: Double,
                         override val id: Option[DatabaseIDScala] = None
                       ) extends LocationPointScala with DatabaseObjectScala
