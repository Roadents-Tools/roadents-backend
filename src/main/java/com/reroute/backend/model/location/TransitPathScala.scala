package com.reroute.backend.model.location

import com.reroute.backend.model.database.{DatabaseIDScala, DatabaseObjectScala}

case class TransitPathScala(
                             transitType: String = "misc",
                             agency: String,
                             route: String,
                             trip: Int,
                             size: Int,
                             override val id: DatabaseIDScala
                           ) extends DatabaseObjectScala
