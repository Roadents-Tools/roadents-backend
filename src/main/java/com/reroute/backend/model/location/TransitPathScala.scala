package com.reroute.backend.model.location

import com.reroute.backend.model.database.{DatabaseIDScala, DatabaseObjectScala}

case class TransitPathScala(
                             agency: String,
                             route: String,
                             trip: Int,
                             size: Int,
                             override val id: Option[DatabaseIDScala] = None
                           ) extends DatabaseObjectScala
