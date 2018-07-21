package com.reroute.backend.stations.test

import com.reroute.backend.model.database.DatabaseID
import com.reroute.backend.model.location.{InputLocation, Station}
import com.reroute.backend.model.time.{TimeDelta, TimePoint}
import com.reroute.backend.stations.WalkableRequest
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
      .max * 1.01
    val req = WalkableRequest(
      center,
      range,
      TimePoint.apply((4 * 365.25 * TimeDelta.DAY).unixdelta, "GMT")
        .withPackedTime(0)
        .withDayOfMonth(30)
        .withDayOfWeek(3)
        .withHour(12)
        .withMinute(0)
        .withSecond(0),
      range.distance / TimeDelta.AVG_WALK_SPEED_PER_MINUTE.distance * TimeDelta.MINUTE
    )

    val res = testdb.getWalkableStations(List(req)).head._2
    assertTrue(s"Got empty res for req: $req", res.nonEmpty)
    assertTrue(res.forall(st => (center distanceTo st.station) <= range))
    res.foreach(stwr => {
      val st = stwr.station
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
      val randlng = -123 + rng.nextDouble()
      val num = randlat.hashCode() * 31 + randlng.hashCode()
      val stat = Station("Test stat:" + num, randlat, randlng, DatabaseID("TEST", -1))
      val range = Seq(InputLocation(37.5, -123), InputLocation(37.5, -121), InputLocation(38, -122), InputLocation(37, -122))
        .map(pt => pt distanceTo stat)
        .min * rng.nextDouble() * 0.1
      WalkableRequest(
        stat,
        range,
        TimePoint.NULL.withPackedTime(0),
        range.distance / TimeDelta.AVG_WALK_SPEED_PER_MINUTE.distance * TimeDelta.MINUTE
      )
    }).filter(req => req.maxdist.distance > 1000)

    val resMap = testdb.getWalkableStations(reqs)
    for ((req, res) <- resMap) {
      assertTrue(s"Got empty req: $req", res.nonEmpty)

      for (stwr <- res) {
        val st = stwr.station
        assertTrue(s"Got bad distance from $st, $req.", (req.center distanceTo st) <= req.maxdist)

        val latLngFormatted = (st.latitude % 0.01 < 0.00001 || st.latitude % 0.01 > 0.0099999) &&
          (Math.abs(st.longitude) % 0.01 < 0.00001 || Math.abs(st.longitude) % 0.01 > 0.0099999)
        assertTrue(s"Found bad station $st.", latLngFormatted)
      }
    }
  }
}
