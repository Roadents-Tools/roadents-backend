package com.reroute.backend.stations.transitland

import java.net.URL
import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.reroute.backend.model.distance.{DistUnits, Distance}
import com.reroute.backend.model.location.LocationPoint
import okhttp3.{OkHttpClient, Request}
import org.json.JSONObject

/**
 * Created by ilan on 1/22/17.
 */
object TransitlandApi {
  private val BASE_FEED_URL = "http://transit.land/api/v1/feeds"
  private val MILES_TO_LAT = 1.0 / 70
  private val MILES_TO_LONG = 1.0 / 70
}

class TransitlandApi {
  private def callUrl(url: String): JSONObject = {
    val client = new OkHttpClient.Builder()
      .connectTimeout(120, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(120, TimeUnit.SECONDS)
      .build()
    val request = new Request.Builder().url(url).build
    val response = client.newCall(request).execute
    if (!response.isSuccessful) return new JSONObject()
    new JSONObject(response.body.string)
  }

  def getFeedsInArea(center: LocationPoint, range: Distance, restrict: util.Map[String, String],
                     avoid: util.Map[String, String]): util.List[URL] = {
    var url = TransitlandApi.BASE_FEED_URL + "?per_page=500"
    if (center != null && range != null && range.in(DistUnits.METERS) >= 0) {
      val latVal1 = center.latitude - range.in(DistUnits.MILES) * TransitlandApi.MILES_TO_LAT
      val latVal2 = center.latitude + range.in(DistUnits.MILES) * TransitlandApi.MILES_TO_LAT
      val lngVal1 = center.longitude - range.in(DistUnits.MILES) * TransitlandApi.MILES_TO_LONG
      val lngVal2 = center.longitude + range.in(DistUnits.MILES) * TransitlandApi.MILES_TO_LONG
      url = s"$url&bbox=$lngVal1,$latVal1,$lngVal2,$latVal2"
    }
    val obj = callUrl(url)
    if (obj.keySet().isEmpty) return Collections.emptyList()
    val feeds = obj.getJSONArray("feeds")
    val len = feeds.length
    val rval = new util.ArrayList[URL](len)
    (0 until len)
      .map(feeds.getJSONObject)
      .filter(meetsFilter(restrict, avoid))
      .map(obj => new URL(obj.getString("url")))
      .foreach(rval.add)
    rval
  }

  def meetsFilter(restrict: util.Map[String, String],
                  avoid: util.Map[String, String]): JSONObject => Boolean = curobj => {
    val restrictEmpty = restrict == null || restrict.isEmpty
    val restrictWorks = restrictEmpty || restrict.keySet.stream.allMatch(key => curobj.get(key) == restrict.get(key))

    val avoidEmpty = avoid == null || avoid.isEmpty
    val avoidWorks = avoidEmpty || avoid.keySet.stream.noneMatch(key => curobj.get(key) == avoid.get(key))

    restrictWorks && avoidWorks
  }
}