package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.distance.DistUnits
import com.reroute.backend.model.location.{InputLocation, Station}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.{PathsRequest, TransferRequest}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random

class TestStationDbScalaTest extends AssertionsForJUnit {

  @Test
  def areasTest(): Unit = {
    val testdb = new TestStationDb()
    val center = Station("CENTER_OF_TESTDB", 37.5, -122, DatabaseID("TEST", -1))
    val range = Seq(InputLocation(37.5, -123), InputLocation(37.5, -121), InputLocation(38, -122), InputLocation(37, -122))
      .map(pt => pt distanceTo center)
      .min * 0.1
    val req = TransferRequest(center, range)
    val area = Math.PI * Math.pow(range.in(DistUnits.KILOMETERS), 2)

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
    val testdb = new TestStationDb()
    val rng = new Random()
    val reqs = (0 to 4).map(_ => {
      val randlat = 37 + rng.nextDouble()
      val randlng = -123 * 2 * rng.nextDouble()
      val num = randlat.hashCode() * 31 + randlng.hashCode()
      val stat = Station("Test stat:" + num, randlat, randlng, DatabaseID("TEST", -1))
      val range = Seq(InputLocation(37.5, -123), InputLocation(37.5, -121), InputLocation(38, -122), InputLocation(37, -122))
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
    val testdb = new TestStationDb()
    val center = Station("CENTER_OF_TESTDB", 37.5, -122, DatabaseID("TEST", -1))
    val delta = TimeDelta(15 * 60 * 1000)
    val req = PathsRequest(center, TimePoint.now().withDayOfWeek(0).withPackedTime(0), delta)
    val res = testdb.getPathsForStation(List(req)).head._2
    assertTrue(res.lengthCompare(4) == 0)
    println(s"Min scheds: ${res.map(_.schedule.size).min}, max: ${res.map(_.schedule.size).max}")
  }
}
