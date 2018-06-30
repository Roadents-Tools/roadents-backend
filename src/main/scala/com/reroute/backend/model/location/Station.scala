package com.reroute.backend.model.location

import com.reroute.backend.model.database.{DatabaseID, DatabaseModel}

case class Station(
                    name: String,
                    override val latitude: Double,
                    override val longitude: Double,
                    override val id: DatabaseID
                  ) extends LocationPoint with DatabaseModel
