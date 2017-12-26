package com.reroute.backend.stations.gtfs

import java.io.File
import java.net.URL
import java.nio.file.{Files, StandardCopyOption}
import java.sql.{DriverManager, Statement}
import java.util.Properties

import org.onebusaway.gtfs.GtfsDatabaseLoaderMain

import scala.util.Try

class GtfsPostgresLoader(val url: URL) {

  val source: String = url.toString
  val zipFileOpt = Try {
    val zipFile = new File(url.getFile.replaceAll("/", "__"))
    zipFile.delete
    zipFile.createNewFile
    zipFile.setWritable(true)
    System.setProperty("jsse.enableSNIExtension", "false")
    System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true")
    val basecon = url.openConnection()
    println("OPENED BASE!")
    val trurl = if (basecon.getHeaderField("Location") == null) url else new URL(basecon.getHeaderField("Location"))
    println("GOT TRUE!")
    val con = if (trurl == url) basecon else trurl.openConnection()
    val inp = con.getInputStream
    println("INPD")
    Files.copy(inp, zipFile.toPath, StandardCopyOption.REPLACE_EXISTING)
    println("COPIED!")
    zipFile
  }

  def load(db: String, userName: String = "donut", userPass: String = "donutpass"): Try[Boolean] = {

    val dbconOpt = Try {
      Class.forName("org.postgresql.Driver")
      val props = new Properties()
      props.setProperty("user", userName)
      props.setProperty("password", userPass)
      props.setProperty("ssl", "true")
      props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
      props.setProperty("sslcompression", "true")
      DriverManager.getConnection(db, props)
    }

    val res = for (zip <- zipFileOpt; con <- dbconOpt) yield {
      val args = Array(
        s"--username=$userName",
        s"--password=$userPass",
        s"--url=$db",
        "--driverClass=org.postgresql.Driver",
        zip.getAbsolutePath
      )
      GtfsDatabaseLoaderMain.main(args)

      val feedStatement = con.createStatement()
      feedStatement.execute(
        s"""INSERT INTO metadata(feed_static_url, update_time) VALUES ('$source', ${System.currentTimeMillis() / 1000})""",
        Statement.RETURN_GENERATED_KEYS)
      val rs = feedStatement.getGeneratedKeys
      rs.next()
      val dataId = rs.getInt("id")
      feedStatement.close()

      val updateStatements = con.createStatement()
      updateStatements.addBatch(s"UPDATE gtfs_agencies SET metadata_id=$dataId WHERE metadata_id IS NULL")
      updateStatements.addBatch(s"UPDATE gtfs_feed_info SET metadata_id=$dataId WHERE metadata_id IS NULL")
      updateStatements.addBatch(s"UPDATE gtfs_stops SET latlng=ST_POINT(lon, lat)::geography WHERE latlng IS NULL")
      updateStatements.addBatch(
        s"""update gtfs_trips
           set stop_time_count = (
             select max(stopsequence) from gtfs_stop_times
             where gtfs_stop_times.trip_id = id and gtfs_stop_times.trip_agencyid = agencyid
             group by (gtfs_stop_times.trip_agencyid, gtfs_stop_times.trip_id)
           )"""
      )

      updateStatements.executeBatch()
      updateStatements.close()
      true
    }
    dbconOpt.foreach(_.close())
    res
  }

}