package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseIDScala
import com.reroute.backend.model.distance.DistanceUnitsScala
import com.reroute.backend.model.location.{StartScala, StationScala}
import com.reroute.backend.model.time.{TimeDeltaScala, TimePointScala}
import com.reroute.backend.stations.{PathsRequest, TransferRequest}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class TestStationDbScalaTest extends AssertionsForJUnit {

  @Test
  def areasTest(): Unit = {
    val testdb = new TestStationDbScala()
    val center = StationScala("CENTER_OF_TESTDB", 37.5, -122, DatabaseIDScala("TEST", -1))
    val range = Seq(StartScala(37.5, -123), StartScala(37.5, -121), StartScala(38, -122), StartScala(37, -122))
      .map(pt => pt distanceTo center)
      .min * 0.1
    val req = TransferRequest(center, range)
    val area = Math.PI * Math.pow(range.in(DistanceUnitsScala.KILOMETERS), 2)

    assertTrue(testdb.servesPoint(center))
    assertTrue(testdb.servesArea(center, range))

    val res = testdb.getTransferStations(List(req)).head._2
    assertTrue(res.nonEmpty)
    assertTrue(res.forall(st => (center distanceTo st) <= range))
    res.foreach(st => {
      val works = (st.latitude % 0.01 < 0.00001 || st.latitude % 0.01 > 0.0099999) &&
        (Math.abs(st.longitude) % 0.01 < 0.00001 || Math.abs(st.longitude) % 0.01 > 0.0099999)
      assertTrue(s"Found bad station $st => (${Math.abs(st.latitude) % .02}, ${Math.abs(st.longitude) % .02}", works)
    })
  }

  @Test
  def bulkAreaTest(): Unit = {
    val testdb = new TestStationDbScala()
    val rng = new Random()
    val reqs = (0 to 4).map(_ => {
      val randlat = 37 + rng.nextDouble()
      val randlng = -123 * 2 * rng.nextDouble()
      val num = randlat.hashCode() * 31 + randlng.hashCode()
      val stat = StationScala("Test stat:" + num, randlat, randlng, DatabaseIDScala("TEST", -1))
      val range = Seq(StartScala(37.5, -123), StartScala(37.5, -121), StartScala(38, -122), StartScala(37, -122))
        .map(pt => pt distanceTo stat)
        .min * rng.nextDouble() * 0.1
      TransferRequest(stat, range)
    }).filter(req => testdb.servesArea(req.station, req.distance) && req.distance.distance > 1000)

    val resMap = testdb.getTransferStations(reqs)
    for ((req, res) <- resMap) {

      assertTrue(s"Got empty req: $req", res.nonEmpty)

      for (st <- res) {

        assertTrue(s"Got bad distance from $st, $req.", (req.station distanceTo st) <= req.distance)

        val latLngFormatted = (st.latitude % 0.01 < 0.00001 || st.latitude % 0.01 > 0.0099999) &&
          (Math.abs(st.longitude) % 0.01 < 0.00001 || Math.abs(st.longitude) % 0.01 > 0.0099999)
        assertTrue(s"Found bad station $st.", latLngFormatted)
      }
    }
  }

  @Test
  def pathsTest(): Unit = {
    val testdb = new TestStationDbScala()
    val center = StationScala("CENTER_OF_TESTDB", 37.5, -122, DatabaseIDScala("TEST", -1))
    val delta = TimeDeltaScala(15 * 60 * 1000)
    val req = PathsRequest(center, TimePointScala.now().withDayOfWeek(0).withPackedTime(0), delta)
    val res = testdb.getPathsForStation(List(req)).head._2
    assertTrue(res.lengthCompare(4) == 0)
    println(s"Min scheds: ${res.map(_.schedule.size).min}, max: ${res.map(_.schedule.size).max}")
  }
}
