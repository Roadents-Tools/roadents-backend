package com.reroute.displayers.restcontroller

import com.reroute.backend.logic.ApplicationResult
import com.reroute.backend.logic.donut.{DonutCore, DonutRequest}
import com.reroute.backend.model.json.RouteJsonOutputer
import spark.{Request, Response, Spark}

import scala.collection.JavaConverters._

object SparkHandler {

  def main(args: Array[String]): Unit = {
    Spark.get("/generator", runDonut)
    Spark.before((_, response) => {
      response.header("Access-Control-Allow-Origin", "*")
    })
  }

  def runDonut(req: Request, res: Response): String = {
    res.`type`("Application/JSON")
    val params = req.queryMap.toMap.asScala.map(params => params._1 -> params._2(0)).toMap
    val algoReq = DonutRequest.buildQuery(params) match {
      case Right(reqa) => reqa
      case Left(err) =>
        res.status(400)
        return s"""{ "error" : 400, "message" : "${err.replace("\"", "\\\"")}" }"""
    }
    DonutCore.runLogic(algoReq) match {
      case ApplicationResult.Result(items) =>
        res.status(200)
        items.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
      case ApplicationResult.Error(errs) =>
        res.status(500)
        errs.mkString("""{ "error" : 500, "message" : " """, ",  ", " \"} ")
    }
  }
}