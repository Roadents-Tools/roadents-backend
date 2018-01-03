package com.reroute.backend.utils.postgres

import com.reroute.backend.model.distance.Distance
import com.reroute.backend.model.location.LocationPoint


/**
 * Parameters for connecting to a Postgresql database.
 *
 * @param dburl  the JDBC-style URL to connect to
 * @param dbname the unique "name" of the database; defaults to the URL
 * @param user   the username of the database user; defaults to "donut"
 * @param pass   the password of the database user; defaults to "donutpass"
 * @param area   the area that this database serves, or None if the database serves everywhere
 */
case class PostgresConfig(
                           dburl: String,
                           dbname: String = "",
                           user: String = "donut",
                           pass: String = "donutpass",
                           area: Option[(LocationPoint, Distance)] = None
                         )

