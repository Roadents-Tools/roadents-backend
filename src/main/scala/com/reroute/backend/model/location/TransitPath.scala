package com.reroute.backend.model.location

import com.reroute.backend.model.database.{DatabaseID, DatabaseModel}

case class TransitPath(
                        agency: String,
                        route: String,
                        trip: String,
                        size: Int,
                        override val id: DatabaseID,
                        transitType: String = "misc"
                      ) extends DatabaseModel
