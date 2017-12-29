package com.reroute.displayers.restcontroller

import com.reroute.backend.logic.ApplicationResultScala
import com.reroute.backend.logic.generator.{GeneratorCoreScala, GeneratorRequest}
import com.reroute.backend.model.json.RouteJsonOutputer
import spark.{Request, Response, Spark}

import scala.collection.JavaConverters._

object SparkHandlerScala {

  def main(args: Array[String]): Unit = {
    Spark.get("/generator", runGenerator)
    Spark.before((_, response) => {
      response.header("Access-Control-Allow-Origin", "*")
    })
  }

  def runGenerator(req: Request, res: Response): String = {
    res.`type`("Application/JSON")
    val params = req.queryMap.toMap.asScala.map(params => params._1 -> params._2(0)).toMap
    val algoReq = GeneratorRequest.buildQuery(params) match {
      case Right(reqa) => reqa
      case Left(err) =>
        res.status(400)
        return s"""{ "error" : 400, "message" : "${err.replace("\"", "\\\"")}" }"""
    }
    GeneratorCoreScala.runLogic(algoReq) match {
      case ApplicationResultScala.Result(items) =>
        res.status(200)
        items.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
      case ApplicationResultScala.Error(errs) =>
        res.status(500)
        errs.mkString("""{ "error" : 500, "message" : " """, ",  ", " \"} ")
    }
  }
}