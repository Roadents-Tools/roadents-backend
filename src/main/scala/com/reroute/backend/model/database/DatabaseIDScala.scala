package com.reroute.backend.model.database

case class DatabaseIDScala(database: String, id: String)

object DatabaseIDScala {
  def apply(database: String, id: Long): DatabaseIDScala = new DatabaseIDScala(database, id + "")
}
