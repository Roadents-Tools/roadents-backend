package com.reroute.backend.model.database

case class DatabaseID(database: String, id: String)

object DatabaseID {
  def apply(database: String, id: Long): DatabaseID = new DatabaseID(database, id + "")
}
