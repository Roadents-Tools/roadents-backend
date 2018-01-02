package com.reroute.displayers.restcontroller

import com.reroute.backend.logic.donut.{DonutCore, DonutRequest}
import com.reroute.backend.logic.interfaces.LogicCore
import com.reroute.backend.logic.{ApplicationRequest, ApplicationResult, RequestMapper}
import com.reroute.backend.model.json.RouteJsonOutputer
import spark.{Request, Response, Spark}

import scala.collection.JavaConverters._

object SparkHandler {

  val paths: Seq[SparkArg[_ <: ApplicationRequest]] = Seq(
    SparkArg("/generator", DonutCore, DonutRequest)
  )

  def main(args: Array[String]): Unit = {
    paths.foreach(arg => Spark.get(arg.path, runPath(arg, _, _)))
    Spark.before((_, response) => {
      response.header("Access-Control-Allow-Origin", "*")
    })
  }

  def runPath[T <: ApplicationRequest](arg: SparkArg[T], req: Request, res: Response): String = {
    res.`type`("Application/JSON")
    val params = req.queryMap.toMap.asScala.map(params => params._1 -> params._2(0)).toMap
    val logicReq = arg.parser.buildQuery(params) match {
      case Right(good) => good
      case Left(err) =>
        res.status(400)
        return s"""{ "error" : 400, "message" : "${err.replace("\"", "\\\"")}" }"""
    }
    arg.core.runLogic(logicReq) match {
      case ApplicationResult.Result(items) =>
        res.status(200)
        items.map(RouteJsonOutputer.output).mkString("[", ", ", "]")
      case ApplicationResult.Error(errs) =>
        res.status(500)
        errs.mkString("""{ "error" : 500, "message" : " """, ",  ", " \"} ")
    }
  }
}

case class SparkArg[T <: ApplicationRequest](path: String, core: LogicCore[T], parser: RequestMapper[T])